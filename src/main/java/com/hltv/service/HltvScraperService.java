package com.hltv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hltv.config.ScraperConfig;
import com.hltv.model.Match;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HltvScraperService {

    private final BrowserService browserService;
    private final ScraperConfig config;
    private final ObjectMapper objectMapper;
    private final CaptchaSolverService captchaSolver;
    private final Map<String, Match> liveMatchesCache = new ConcurrentHashMap<>();
    private final Map<String, Match> upcomingMatchesCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> missingMatchTracker = new ConcurrentHashMap<>();
    private Page currentPage;
    private boolean isScraperRunning = false;
    private LocalDateTime lastPageRefresh = null;

    public HltvScraperService(BrowserService browserService, ScraperConfig config, ObjectMapper objectMapper, CaptchaSolverService captchaSolver) {
        this.browserService = browserService;
        this.config = config;
        this.objectMapper = objectMapper;
        this.captchaSolver = captchaSolver;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing HLTV scraper service");
        // Only start scraping if browser is available
        if (browserService.isBrowserAvailable()) {
            log.info("Browser is available, starting initial scrape");
            new Thread(this::startScraping).start();
        } else {
            log.warn("Browser is not available yet - will start scraping when browser becomes ready");
            // Start a thread to wait for browser availability
            new Thread(() -> {
                while (!browserService.isBrowserAvailable() && !browserService.isShuttingDown()) {
                    try {
                        Thread.sleep(5000); // Check every 5 seconds
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (browserService.isBrowserAvailable() && !isScraperRunning) {
                    log.info("Browser is now available, starting initial scrape");
                    startScraping();
                }
            }).start();
        }
    }

    @Scheduled(fixedDelay = 1800000) // Re-scrape every 30 minutes
    public void scheduledScrape() {
        if (!isScraperRunning && browserService.isBrowserAvailable()) {
            new Thread(this::startScraping).start();
        }
    }

    private void startScraping() {
        // Double-check browser availability before starting
        if (!browserService.isBrowserAvailable()) {
            log.warn("Cannot start scraping - browser is not available");
            return;
        }

        isScraperRunning = true;
        try {
            log.info("Starting HLTV scraping session");
            currentPage = browserService.createStealthPage();

            // Navigate to HLTV matches page with advanced anti-detection
            navigateWithRetry("https://www.hltv.org/matches");

            // Wait for page to load and handle any captchas
            handleCaptchaAndWaitForLoad();

            // Parse initial data
            parseMatchData();

            // Setup WebSocket interception for live updates
            setupWebSocketInterception();

            // Keep page alive for configured duration with random variance
            long duration = config.getScraper().getPageTimeout() +
                ThreadLocalRandom.current().nextLong(-config.getScraper().getRandomVariance(),
                                                     config.getScraper().getRandomVariance());

            log.info("Keeping page alive for {} ms to capture live updates", duration);

            // Periodically refresh data while page is alive
            long endTime = System.currentTimeMillis() + duration;
            lastPageRefresh = LocalDateTime.now();

            while (System.currentTimeMillis() < endTime) {
                try {
                    Thread.sleep(30000); // Check every 30 seconds

                    // Check if page is still valid
                    if (currentPage == null || currentPage.isClosed()) {
                        log.warn("Page closed unexpectedly, breaking loop");
                        break;
                    }

                    parseMatchData();

                    // Check if we need to refresh the page
                    RefreshDecision decision = shouldRefreshPage();
                    if (decision.shouldRefresh) {
                        log.info("Refreshing page: {}", decision.reason);
                        refreshPage();
                        lastPageRefresh = LocalDateTime.now();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Scraping interrupted");
                    break;
                } catch (Exception e) {
                    log.error("Error in scraping loop: {}", e.getMessage());
                    // Continue loop despite error
                }
            }

        } catch (Exception e) {
            log.error("Error during scraping session", e);
        } finally {
            cleanup();
            isScraperRunning = false;
        }
    }

    private void navigateWithRetry(String url) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                log.info("Navigating to {} (attempt {})", url, attempt + 1);

                // Add random delay to appear more human-like
                Thread.sleep(ThreadLocalRandom.current().nextLong(2000, 5000));

                currentPage.navigate(url, new Page.NavigateOptions().setTimeout(60000));

                log.info("Successfully navigated to {}", url);
                return;

            } catch (Exception e) {
                attempt++;
                log.warn("Navigation attempt {} failed: {}", attempt, e.getMessage());

                if (attempt >= maxRetries) {
                    throw new RuntimeException("Failed to navigate after " + maxRetries + " attempts", e);
                }

                try {
                    Thread.sleep(5000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handleCaptchaAndWaitForLoad() {

        // Check for common captcha indicators (only visible ones)
        boolean hasCaptcha = checkForCaptcha();

        if (hasCaptcha) {
            log.warn("Visible captcha detected, attempting to handle it...");
            handleCaptcha();
        } else {
            log.info("No visible captcha blocking page load");
        }

        try {
            log.info("Waiting for page load and checking for captcha...");

            // Wait a bit for page to load
            Thread.sleep(2000);

            // Handle cookie consent first - this is critical for WebSocket to work
            try {
                // Try multiple selectors for cookie consent
                String[] cookieSelectors = {
                    "button:has-text('Allow all cookies')",
                    "button:has-text('Accept')",
                    ".acceptAll",
                    "#onetrust-accept-btn-handler",
                    "[id*='accept'][id*='btn']",
                    "button[class*='accept']",
                    "button[class*='cookie']"
                };

                boolean clicked = false;
                for (String selector : cookieSelectors) {
                    try {
                        Locator button = currentPage.locator(selector).first();
                        if (button.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                            log.info("Found and clicking cookie consent button: {}", selector);
                            button.click();
                            Thread.sleep(3000); // Wait for modal to close and page to initialize
                            clicked = true;
                            break;
                        }
                    } catch (Exception btnEx) {
                        // Try next selector
                    }
                }

                if (!clicked) {
                    log.debug("No cookie consent button found (may already be accepted)");
                }
            } catch (Exception e) {
                log.debug("Error handling cookie consent: {}", e.getMessage());
            }

            // Wait for matches container to be visible
            try {
                currentPage.waitForSelector(".match-wrapper, .matches-list",
                    new Page.WaitForSelectorOptions().setTimeout(30000));
                log.info("Match sections loaded successfully");

                // Wait for live match scores to render if there are live matches
                try {
                    currentPage.waitForSelector(".match-team-livescore .match-team-score",
                        new Page.WaitForSelectorOptions().setTimeout(5000));
                    log.info("Live match scores loaded");
                } catch (Exception scoreEx) {
                    log.debug("No live match scores found (might not be any live matches)");
                }
            } catch (Exception e) {
                log.warn("Could not find match sections, attempting to debug page content");

                // Debug: Log what's actually on the page
                try {
                    String debugInfo = (String) currentPage.evaluate("""
                        () => {
                            const info = {
                                title: document.title,
                                url: window.location.href,
                                hasLiveMatchesSection: !!document.querySelector('.liveMatchesSection'),
                                hasLiveMatchContainer: !!document.querySelector('.live-match-container'),
                                hasUpcomingMatch: !!document.querySelector('.upcoming-match'),
                                liveMatchCount: document.querySelectorAll('.live-match-container').length,
                                upcomingMatchCount: document.querySelectorAll('.upcoming-match').length,
                                allMatchWrappers: document.querySelectorAll('.match-wrapper').length,
                                bodyClasses: document.body.className,
                                hasMatchesList: !!document.querySelector('.matches-list')
                            };
                            return JSON.stringify(info, null, 2);
                        }
                        """);
                    log.info("Page debug info: {}", debugInfo);
                } catch (Exception debugEx) {
                    log.warn("Could not get debug info: {}", debugEx.getMessage());
                }
            }

            // Sort matches by time before scraping
            try {
                log.info("Clicking sort-by-time button to organize matches chronologically");
                currentPage.click(".matches-sort-by-toggle-time");
                Thread.sleep(2000); // Wait for page to re-render with time-sorted matches
                log.info("Matches sorted by time successfully");
            } catch (Exception sortEx) {
                log.warn("Could not click sort-by-time button: {}", sortEx.getMessage());
            }

            // Scores populate via Socket.IO after page load
            // Just wait a few seconds for Socket.IO to connect and scores to populate
            log.info("Waiting for Socket.IO to connect and scores to populate...");
            Thread.sleep(3000);

        } catch (Exception e) {
            log.error("Error handling captcha and page load", e);
        }
    }

    private boolean checkForCaptcha() {
        try {
            // Check for Cloudflare challenge or visible captcha elements
            Boolean hasChallenge = (Boolean) currentPage.evaluate("""
                () => {
                    // Check if page title indicates Cloudflare challenge
                    if (document.title.includes('Just a moment') || document.title.includes('Attention Required')) {
                        console.log('Cloudflare challenge page detected from title');
                        return true;
                    }

                    // Check for Cloudflare challenge form
                    if (document.querySelector('#challenge-form') || document.querySelector('[name="cf-turnstile-response"]')) {
                        console.log('Cloudflare challenge form detected');
                        return true;
                    }

                    // Check for visible captcha/challenge elements
                    const selectors = [
                        'iframe[src*="captcha"]',
                        'iframe[src*="recaptcha"]',
                        '.cf-challenge-running',
                        '#challenge-form',
                        '.g-recaptcha'
                    ];

                    for (let selector of selectors) {
                        const el = document.querySelector(selector);
                        if (el) {
                            // Check if element is actually visible on screen
                            const rect = el.getBoundingClientRect();
                            const style = window.getComputedStyle(el);

                            const isVisible = rect.width > 0 &&
                                            rect.height > 0 &&
                                            style.display !== 'none' &&
                                            style.visibility !== 'hidden' &&
                                            style.opacity !== '0' &&
                                            el.offsetParent !== null;

                            if (isVisible) {
                                console.log('Found visible challenge:', selector);
                                return true;
                            }
                        }
                    }
                    return false;
                }
                """);

            if (hasChallenge != null && hasChallenge) {
                log.info("Detected captcha/challenge blocking the page");
                return true;
            }

            log.debug("No captcha/challenge detected");
            return false;
        } catch (Exception e) {
            log.warn("Error checking for captcha", e);
            return false;
        }
    }

    private void handleCaptcha() {
        try {
            log.info("Attempting to bypass captcha/challenge...");

            // For Cloudflare managed challenges, we need to wait for auto-solve
            // Strategy 1: Wait and check multiple times
            log.info("Waiting for Cloudflare challenge to auto-solve...");
            int maxAttempts = 12; // 12 * 5 seconds = 60 seconds total

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                Thread.sleep(5000);

                // Check page title to see if we've passed the challenge
                String title = currentPage.title();
                log.info("Attempt {}/{}: Page title = '{}'", attempt, maxAttempts, title);

                if (!title.contains("Just a moment") && !title.contains("Attention Required")) {
                    log.info("Challenge appears to be resolved after {} seconds", attempt * 5);
                    return;
                }

                // Simulate human behavior periodically
                if (attempt % 3 == 0) {
                    log.debug("Simulating human behavior...");
                    simulateHumanBehavior();
                }
            }

            // Strategy 2: If still showing challenge after 60 seconds, check one more time
            if (checkForCaptcha()) {
                log.warn("Challenge still present after 60 seconds");
            }

            // Strategy 5: If still present, try 2captcha solver
            if (checkForCaptcha() && config.getCaptcha().isEnabled()) {
                log.info("Captcha still present, attempting to solve with 2captcha...");

                // Take screenshot for debugging
                try {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    java.nio.file.Path screenshotDir = java.nio.file.Paths.get("screenshots");
                    if (!java.nio.file.Files.exists(screenshotDir)) {
                        java.nio.file.Files.createDirectories(screenshotDir);
                    }
                    java.nio.file.Path screenshotPath = screenshotDir.resolve("captcha_" + timestamp + ".png");
                    currentPage.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));
                    log.info("Saved captcha screenshot to {}", screenshotPath.toAbsolutePath());
                } catch (Exception e) {
                    log.warn("Could not save screenshot: {}", e.getMessage());
                }

                // Debug: log page HTML snippet and challenge details
                try {
                    String challengeInfo = (String) currentPage.evaluate("""
                        () => {
                            const info = {
                                title: document.title,
                                url: window.location.href,
                                hasRecaptcha: !!document.querySelector('.g-recaptcha'),
                                hasChallenge: !!document.querySelector('#challenge-form'),
                                hasTurnstileInput: !!document.querySelector('[name="cf-turnstile-response"]'),
                                challengeFormHTML: document.querySelector('#challenge-form')?.innerHTML?.substring(0, 500),
                                bodyClasses: document.body?.className,
                                iframes: Array.from(document.querySelectorAll('iframe')).map(i => i.src).slice(0, 3),
                                scripts: Array.from(document.querySelectorAll('script[src]'))
                                    .map(s => s.src)
                                    .filter(src => src.includes('turnstile') || src.includes('challenge') || src.includes('captcha'))
                                    .slice(0, 3)
                            };
                            return JSON.stringify(info, null, 2);
                        }
                        """);
                    log.info("Challenge page info: {}", challengeInfo);
                } catch (Exception e) {
                    log.warn("Could not get challenge info: {}", e.getMessage());
                }

                // Save page HTML for debugging
                try {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    java.nio.file.Path htmlPath = java.nio.file.Paths.get("screenshots").resolve("captcha_" + timestamp + ".html");
                    String pageHtml = currentPage.content();
                    java.nio.file.Files.writeString(htmlPath, pageHtml);
                    log.info("Saved page HTML to {}", htmlPath.toAbsolutePath());
                } catch (Exception e) {
                    log.warn("Could not save page HTML: {}", e.getMessage());
                }

                // Try reCAPTCHA first
                String recaptchaSiteKey = captchaSolver.detectRecaptchaSiteKey(currentPage);
                if (recaptchaSiteKey != null) {
                    log.info("Detected reCAPTCHA with site key: {}", recaptchaSiteKey);
                    boolean solved = captchaSolver.solveRecaptchaV2(currentPage, recaptchaSiteKey);
                    if (solved) {
                        log.info("Successfully solved reCAPTCHA");
                        Thread.sleep(3000); // Wait for page to process solution
                        return;
                    }
                } else {
                    log.warn("Could not detect reCAPTCHA site key");
                }

                // Try Cloudflare Turnstile
                log.info("Attempting to detect Cloudflare Turnstile site key...");
                String turnstileSiteKey = captchaSolver.detectTurnstileSiteKey(currentPage);
                if (turnstileSiteKey != null) {
                    log.info("Detected Cloudflare Turnstile with site key: {}", turnstileSiteKey);
                    boolean solved = captchaSolver.solveTurnstile(currentPage, turnstileSiteKey);
                    if (solved) {
                        log.info("Successfully solved Turnstile");
                        Thread.sleep(3000);
                        return;
                    } else {
                        log.warn("Turnstile solving failed - check 2captcha balance and API key");
                    }
                } else {
                    log.warn("Could not detect Turnstile site key - the challenge page may not have loaded the widget yet or may be a different type");
                    log.warn("This usually means Cloudflare is showing an auto-solving challenge that doesn't require manual solving");
                }

                log.warn("Could not detect or solve captcha type - may need to wait longer or use different approach");
            }

            // Final check
            if (checkForCaptcha()) {
                log.warn("Captcha still present after all attempts - may require manual intervention");
            } else {
                log.info("Successfully bypassed captcha");
            }

        } catch (Exception e) {
            log.error("Error handling captcha", e);
        }
    }

    private void simulateHumanBehavior() {
        try {
            // Move mouse randomly
            for (int i = 0; i < 5; i++) {
                int x = ThreadLocalRandom.current().nextInt(100, 800);
                int y = ThreadLocalRandom.current().nextInt(100, 600);
                currentPage.mouse().move(x, y);
                Thread.sleep(ThreadLocalRandom.current().nextLong(100, 500));
            }

            // Scroll a bit
            currentPage.evaluate("window.scrollBy(0, " + ThreadLocalRandom.current().nextInt(100, 300) + ")");
            Thread.sleep(1000);

        } catch (Exception e) {
            log.warn("Error simulating human behavior", e);
        }
    }

    private void parseMatchData() {
        try {
            log.debug("Parsing match data from page");

            // Check if page is still open
            if (currentPage == null || currentPage.isClosed()) {
                log.warn("Cannot parse match data - page is closed");
                return;
            }

            // Parse live matches
            List<Match> liveMatches = parseMatches(true);
            liveMatches.forEach(match -> liveMatchesCache.put(match.getMatchId(), match));

            // Parse upcoming matches
            List<Match> upcomingMatches = parseMatches(false);
            upcomingMatches.forEach(match -> upcomingMatchesCache.put(match.getMatchId(), match));

            log.info("Parsed {} live matches and {} upcoming matches",
                liveMatches.size(), upcomingMatches.size());

        } catch (Exception e) {
            log.error("Error parsing match data", e);
        }
    }

    private List<Match> parseMatches(boolean isLive) {
        List<Match> matches = new ArrayList<>();

        try {
            // For live matches, wait longer for scores to render
            if (isLive) {
                try {
                    // Wait for score elements to exist
                    currentPage.waitForSelector(".match-team-livescore .match-team-score",
                        new Page.WaitForSelectorOptions().setTimeout(5000));

                    // Add extra delay for scores to populate
                    Thread.sleep(3000);

                    // Debug: check what scores look like
                    Object scoreDebug = currentPage.evaluate("""
                        () => {
                            const firstMatch = document.querySelector('.match-wrapper[live="true"]');
                            if (!firstMatch) return 'No live matches';

                            const livescoreContainer = firstMatch.querySelector('.match-team-livescore');
                            if (!livescoreContainer) return 'No livescore container';

                            const currentMapScores = livescoreContainer.querySelectorAll('.current-map-score');
                            if (currentMapScores.length < 2) return 'Less than 2 current-map-score elements: ' + currentMapScores.length;

                            return {
                                score1HTML: currentMapScores[0].outerHTML,
                                score2HTML: currentMapScores[1].outerHTML,
                                score1Text: currentMapScores[0].textContent,
                                score2Text: currentMapScores[1].textContent,
                                score1Trimmed: currentMapScores[0].textContent.trim(),
                                score2Trimmed: currentMapScores[1].textContent.trim()
                            };
                        }
                    """);
                    log.info("Live match score debug: {}", scoreDebug);

                } catch (Exception e) {
                    log.warn("Error waiting for scores: {}", e.getMessage());
                }
            }

            // Use Playwright to query and extract match data
            String script = String.format("""
                () => {
                    const matches = [];
                    const selector = %s ? '.match-wrapper[live="true"]' : '.match-wrapper:not([live="true"])';
                    const matchElements = document.querySelectorAll(selector);

                    matchElements.forEach(matchEl => {
                        try {
                            const matchLink = matchEl.querySelector('a[href*="/matches/"]');
                            const matchUrl = matchLink ? matchLink.href : '';
                            const matchId = matchEl.getAttribute('data-match-id') || matchUrl.split('/')[4] || '';

                            const teamEls = matchEl.querySelectorAll('.match-team');
                            const team1El = teamEls[0];
                            const team2El = teamEls[1];

                            const team1Name = team1El?.querySelector('.match-teamname')?.textContent?.trim() || '';
                            const team2Name = team2El?.querySelector('.match-teamname')?.textContent?.trim() || '';

                            const team1Logo = team1El?.querySelector('.match-team-logo')?.src || '';
                            const team2Logo = team2El?.querySelector('.match-team-logo')?.src || '';

                            let team1Score = null;
                            let team2Score = null;
                            let team1MapWins = null;
                            let team2MapWins = null;

                            if (%s) {
                                // For live matches, extract scores
                                // Try multiple methods to find scores

                                // Method 1: Look for .current-map-score in livescore container
                                const livescoreContainer = matchEl.querySelector('.match-team-livescore');

                                // First, extract map wins (series score like 1-0, 1-1 in BO3)
                                // Look in the livescore container for data-livescore-maps-won-for elements
                                if (livescoreContainer) {
                                    const team1MapWinEl = livescoreContainer.querySelector('[data-livescore-maps-won-for][data-livescore-team="' + matchEl.getAttribute('team1') + '"]');
                                    const team2MapWinEl = livescoreContainer.querySelector('[data-livescore-maps-won-for][data-livescore-team="' + matchEl.getAttribute('team2') + '"]');

                                    if (team1MapWinEl) {
                                        const mapWinText = team1MapWinEl.textContent?.trim() || '';
                                        if (mapWinText && /^\\d+$/.test(mapWinText)) {
                                            team1MapWins = parseInt(mapWinText);
                                        }
                                    }
                                    if (team2MapWinEl) {
                                        const mapWinText = team2MapWinEl.textContent?.trim() || '';
                                        if (mapWinText && /^\\d+$/.test(mapWinText)) {
                                            team2MapWins = parseInt(mapWinText);
                                        }
                                    }
                                }
                                if (livescoreContainer) {
                                    const scoreSpans = livescoreContainer.querySelectorAll('.current-map-score');
                                    if (scoreSpans.length >= 2) {
                                        const score1Text = scoreSpans[0]?.textContent?.trim() || '';
                                        const score2Text = scoreSpans[1]?.textContent?.trim() || '';

                                        if (score1Text && /^\\d+$/.test(score1Text)) {
                                            team1Score = parseInt(score1Text);
                                        }
                                        if (score2Text && /^\\d+$/.test(score2Text)) {
                                            team2Score = parseInt(score2Text);
                                        }
                                    }
                                }

                                // Method 2: If no scores found, try .match-team-score
                                if (team1Score === null || team2Score === null) {
                                    const team1ScoreEl = team1El?.querySelector('.match-team-score');
                                    const team2ScoreEl = team2El?.querySelector('.match-team-score');

                                    if (team1ScoreEl) {
                                        const scoreText = team1ScoreEl.textContent?.trim() || '';
                                        if (scoreText && /^\\d+$/.test(scoreText)) {
                                            team1Score = parseInt(scoreText);
                                        }
                                    }
                                    if (team2ScoreEl) {
                                        const scoreText = team2ScoreEl.textContent?.trim() || '';
                                        if (scoreText && /^\\d+$/.test(scoreText)) {
                                            team2Score = parseInt(scoreText);
                                        }
                                    }
                                }

                                // Method 3: Look for any element with 'score' in class name
                                if (team1Score === null || team2Score === null) {
                                    const allScoreElements = matchEl.querySelectorAll('[class*="score"]');
                                    const scores = [];
                                    allScoreElements.forEach(el => {
                                        const text = el.textContent?.trim() || '';
                                        if (text && /^\\d+$/.test(text)) {
                                            scores.push(parseInt(text));
                                        }
                                    });

                                    // Take first two numeric scores found
                                    if (scores.length >= 2 && team1Score === null) {
                                        team1Score = scores[0];
                                        team2Score = scores[1];
                                    }
                                }
                            }

                            const eventEl = matchEl.querySelector('.match-event');
                            const event = eventEl?.getAttribute('data-event-headline') || eventEl?.textContent?.trim() || '';

                            const metaEl = matchEl.querySelector('.match-meta');
                            const format = metaEl?.textContent?.trim() || '';

                            const timeEl = matchEl.querySelector('.match-time');
                            let matchTime = null;
                            let matchTimeUnix = null;

                            if (timeEl) {
                                const unixStr = timeEl.getAttribute('data-unix');
                                if (unixStr) {
                                    matchTimeUnix = parseInt(unixStr);
                                }
                                matchTime = timeEl.textContent?.trim() || '';
                            }

                            if (matchId && team1Name && team2Name) {
                                matches.push({
                                    matchId: matchId,
                                    team1Name: team1Name,
                                    team2Name: team2Name,
                                    team1Logo: team1Logo,
                                    team2Logo: team2Logo,
                                    team1Score: team1Score,
                                    team2Score: team2Score,
                                    team1MapWins: team1MapWins,
                                    team2MapWins: team2MapWins,
                                    format: format,
                                    event: event,
                                    matchTime: matchTime,
                                    matchTimeUnix: matchTimeUnix,
                                    matchUrl: matchUrl,
                                    isLive: %s
                                });
                            }
                        } catch (e) {
                            console.error('Error parsing match:', e);
                        }
                    });

                    return matches;
                }
                """, isLive, isLive, isLive);

            Object result = currentPage.evaluate(script);

            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> matchMaps = (List<Map<String, Object>>) result;

                log.debug("Parsed {} match objects from JavaScript for isLive={}", matchMaps.size(), isLive);

                for (Map<String, Object> matchMap : matchMaps) {
                    try {
                        // Debug log the match data
                        if (isLive) {
                            log.debug("Live match data: matchId={}, roundScore={}:{}, mapWins={}:{}",
                                matchMap.get("matchId"),
                                matchMap.get("team1Score"),
                                matchMap.get("team2Score"),
                                matchMap.get("team1MapWins"),
                                matchMap.get("team2MapWins"));
                        }
                        // Convert Unix timestamp to LocalDateTime
                        LocalDateTime matchTime = null;
                        Object matchTimeUnix = matchMap.get("matchTimeUnix");
                        if (matchTimeUnix != null) {
                            try {
                                long timestamp = ((Number) matchTimeUnix).longValue();
                                matchTime = LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(timestamp),
                                    java.time.ZoneId.systemDefault()
                                );
                            } catch (Exception e) {
                                log.debug("Could not parse match time: {}", e.getMessage());
                            }
                        }

                        Match match = Match.builder()
                            .matchId(String.valueOf(matchMap.get("matchId")))
                            .team1Name(String.valueOf(matchMap.get("team1Name")))
                            .team2Name(String.valueOf(matchMap.get("team2Name")))
                            .team1Logo(String.valueOf(matchMap.get("team1Logo")))
                            .team2Logo(String.valueOf(matchMap.get("team2Logo")))
                            .team1Score(matchMap.get("team1Score") != null ? ((Number) matchMap.get("team1Score")).intValue() : null)
                            .team2Score(matchMap.get("team2Score") != null ? ((Number) matchMap.get("team2Score")).intValue() : null)
                            .team1MapWins(matchMap.get("team1MapWins") != null ? ((Number) matchMap.get("team1MapWins")).intValue() : null)
                            .team2MapWins(matchMap.get("team2MapWins") != null ? ((Number) matchMap.get("team2MapWins")).intValue() : null)
                            .format(String.valueOf(matchMap.get("format")))
                            .event(String.valueOf(matchMap.get("event")))
                            .matchTime(matchTime)
                            .matchUrl(String.valueOf(matchMap.get("matchUrl")))
                            .isLive(isLive)
                            .lastUpdated(LocalDateTime.now())
                            .build();

                        if (!match.getMatchId().isEmpty() && !match.getMatchId().equals("null")) {
                            matches.add(match);
                        }
                    } catch (Exception e) {
                        log.warn("Error building match object", e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error parsing matches", e);
        }

        return matches;
    }

    private void setupWebSocketInterception() {
        try {
            log.info("Setting up WebSocket interception for live score updates");

            // First, debug what WebSocket connections exist
            debugWebSocketConnections();

            // Listen for WebSocket frames
            currentPage.onWebSocket(ws -> {
                log.info("WebSocket connection detected: {}", ws.url());

                ws.onFrameReceived(frame -> {
                    try {
                        String payload = frame.text();
                        log.info("WebSocket frame received from {}: {}", ws.url(), payload);

                        // Parse and update match scores if this is score data
                        parseWebSocketData(payload);

                    } catch (Exception e) {
                        log.warn("Error processing WebSocket frame", e);
                    }
                });

                ws.onFrameSent(frame -> {
                    log.debug("WebSocket frame sent to {}: {}", ws.url(), frame.text());
                });

                ws.onClose(closedEvent -> {
                    log.warn("WebSocket closed: {}", ws.url());
                });
            });

        } catch (Exception e) {
            log.error("Error setting up WebSocket interception", e);
        }
    }

    private void debugWebSocketConnections() {
        try {
            // Check what WebSocket connections the page is trying to make
            Object wsDebug = currentPage.evaluate("""
                () => {
                    const info = {
                        hasWebSocket: typeof WebSocket !== 'undefined',
                        hasSocketIO: typeof io !== 'undefined',
                        socketIOGlobal: window.io ? 'exists' : 'missing',
                        activeConnections: []
                    };

                    // Hook into WebSocket constructor to log all connection attempts
                    const OriginalWebSocket = window.WebSocket;
                    const connections = [];

                    window.WebSocket = function(url, protocols) {
                        console.log('WebSocket connection attempt to:', url);
                        connections.push({url: url, protocols: protocols, timestamp: Date.now()});
                        return new OriginalWebSocket(url, protocols);
                    };
                    window.WebSocket.prototype = OriginalWebSocket.prototype;

                    return info;
                }
            """);
            log.info("WebSocket debug info: {}", wsDebug);

            // Wait a bit to see if any connections are made
            Thread.sleep(3000);

            // Check for Socket.IO specifically (HLTV uses this)
            Object socketIOCheck = currentPage.evaluate("""
                () => {
                    // Check if Socket.IO is loaded and has active connections
                    if (typeof io !== 'undefined') {
                        return {
                            socketIOAvailable: true,
                            ioType: typeof io
                        };
                    }

                    // Check for script tags loading Socket.IO
                    const scripts = Array.from(document.querySelectorAll('script[src]'));
                    const socketIOScript = scripts.find(s => s.src.includes('socket.io'));

                    return {
                        socketIOAvailable: false,
                        socketIOScriptFound: !!socketIOScript,
                        socketIOScriptSrc: socketIOScript ? socketIOScript.src : null,
                        allScripts: scripts.map(s => s.src).filter(src => src.includes('socket') || src.includes('websocket'))
                    };
                }
            """);
            log.info("Socket.IO availability: {}", socketIOCheck);

        } catch (Exception e) {
            log.error("Error debugging WebSocket connections", e);
        }
    }

    private void parseWebSocketData(String payload) {
        try {
            // Try to parse as JSON
            JsonNode node = objectMapper.readTree(payload);

            // Look for score updates - this will depend on HLTV's WebSocket format
            // You may need to adjust this based on actual WebSocket messages
            if (node.has("matchId") && node.has("score")) {
                String matchId = node.get("matchId").asText();
                JsonNode scoreNode = node.get("score");

                Match match = liveMatchesCache.get(matchId);
                if (match != null) {
                    if (scoreNode.has("team1")) {
                        match.setTeam1Score(scoreNode.get("team1").asInt());
                    }
                    if (scoreNode.has("team2")) {
                        match.setTeam2Score(scoreNode.get("team2").asInt());
                    }
                    match.setLastUpdated(LocalDateTime.now());

                    log.info("Updated live score for match {}: {} - {}",
                        matchId, match.getTeam1Score(), match.getTeam2Score());
                }
            }

        } catch (Exception e) {
            // Not JSON or not relevant data, ignore
            log.trace("Could not parse WebSocket data as score update: {}", e.getMessage());
        }
    }

    public List<Match> getLiveMatches() {
        return new ArrayList<>(liveMatchesCache.values());
    }

    public List<Match> getUpcomingMatches() {
        return new ArrayList<>(upcomingMatchesCache.values());
    }

    private static class RefreshDecision {
        boolean shouldRefresh;
        String reason;

        RefreshDecision(boolean shouldRefresh, String reason) {
            this.shouldRefresh = shouldRefresh;
            this.reason = reason;
        }
    }

    private RefreshDecision shouldRefreshPage() {
        LocalDateTime now = LocalDateTime.now();

        // Check if any upcoming match is about to start (within 2 minutes)
        for (Match match : upcomingMatchesCache.values()) {
            if (match.getMatchTime() != null) {
                long minutesUntilStart = java.time.Duration.between(now, match.getMatchTime()).toMinutes();

                if (minutesUntilStart >= -1 && minutesUntilStart <= 2) {
                    // Match is starting soon or just started
                    return new RefreshDecision(true, String.format("Match %s starting in %d minutes", match.getMatchId(), minutesUntilStart));
                }

                // Check if match should be live but isn't
                if (minutesUntilStart < -5) { // Match should have started 5+ minutes ago
                    boolean isLive = liveMatchesCache.containsKey(match.getMatchId());
                    if (!isLive) {
                        // Track this missing match
                        LocalDateTime firstMissed = missingMatchTracker.get(match.getMatchId());
                        if (firstMissed == null) {
                            missingMatchTracker.put(match.getMatchId(), now);
                            log.info("Match {} should be live but isn't. Starting to track it.", match.getMatchId());
                        } else {
                            long minutesMissing = java.time.Duration.between(firstMissed, now).toMinutes();

                            if (minutesMissing < 10) {
                                // Missing for less than 10 minutes, refresh every 2 minutes
                                long minutesSinceLastRefresh = java.time.Duration.between(lastPageRefresh, now).toMinutes();
                                if (minutesSinceLastRefresh >= 2) {
                                    return new RefreshDecision(true, String.format("Match %s missing for %d minutes, refreshing to check", match.getMatchId(), minutesMissing));
                                }
                            } else {
                                // Missing for 10+ minutes
                                // Check if match has been rescheduled
                                Match upcomingMatch = upcomingMatchesCache.get(match.getMatchId());
                                if (upcomingMatch != null && upcomingMatch.getMatchTime() != null) {
                                    long newMinutesUntilStart = java.time.Duration.between(now, upcomingMatch.getMatchTime()).toMinutes();
                                    if (newMinutesUntilStart > 5) {
                                        // Match has been rescheduled to a later time
                                        log.info("Match {} has been rescheduled to {}", match.getMatchId(), upcomingMatch.getMatchTime());
                                        missingMatchTracker.remove(match.getMatchId());
                                    } else {
                                        // Still missing after 10 minutes and not rescheduled, give up on frequent refreshes
                                        log.warn("Match {} missing for 10+ minutes and not rescheduled. Reverting to normal refresh schedule.", match.getMatchId());
                                        missingMatchTracker.remove(match.getMatchId());
                                    }
                                }
                            }
                        }
                    } else {
                        // Match is now live, remove from tracker
                        missingMatchTracker.remove(match.getMatchId());
                    }
                }
            }
        }

        // Default: refresh every 30 minutes
        long minutesSinceLastRefresh = java.time.Duration.between(lastPageRefresh, now).toMinutes();
        if (minutesSinceLastRefresh >= 30) {
            return new RefreshDecision(true, "Regular 30-minute refresh");
        }

        return new RefreshDecision(false, null);
    }

    private void refreshPage() {
        try {
            log.info("Refreshing page to get updated match data and WebSocket connections");

            // Check if page is still open
            if (currentPage.isClosed()) {
                log.error("Cannot refresh - page is already closed");
                return;
            }

            // Navigate to the page again instead of reload (reload can cause issues)
            navigateWithRetry("https://www.hltv.org/matches");

            // Wait for page to load and handle captcha
            handleCaptchaAndWaitForLoad();

            // Parse updated data
            parseMatchData();

            // Re-setup WebSocket interception
            setupWebSocketInterception();

            log.info("Page refresh completed successfully");

        } catch (Exception e) {
            log.error("Error refreshing page: {}", e.getMessage());
            // Don't throw, just log and continue
        }
    }

    private void cleanup() {
        try {
            if (currentPage != null && !currentPage.isClosed()) {
                currentPage.close();
            }
        } catch (Exception e) {
            log.warn("Error cleaning up page", e);
        }
    }
}