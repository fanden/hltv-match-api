package com.hltv.service;

import com.hltv.config.ScraperConfig;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Arrays;

@Service
@Slf4j
public class BrowserService {

    private final ScraperConfig config;
    private Playwright playwright;
    private Browser browser;
    private volatile boolean isShuttingDown = false;
    private volatile boolean browserInitialized = false;

    public BrowserService(ScraperConfig config) {
        this.config = config;
        // Don't initialize browser in constructor - do it lazily when needed
        // This allows the app to start even when offline
    }

    private synchronized void initializeBrowser() {
        if (browserInitialized || isShuttingDown) {
            return;
        }

        try {
            log.info("Initializing browser service...");
            playwright = Playwright.create();

            if (config.getBrowserless().isEnabled()) {
                log.info("Initializing Playwright browser connection to Browserless at {}", config.getBrowserless().getUrl());
                // Connect to remote Browserless instance via CDP
                browser = playwright.chromium().connectOverCDP(config.getBrowserless().getUrl());
                log.info("Successfully connected to Browserless");
            } else {
                log.info("Launching local Chromium browser with visible window");
                log.warn("IMPORTANT: Make sure Playwright browsers are installed by running: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args=\"install chromium\"");
                browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
                    .setHeadless(false) // Visible browser window
                    .setSlowMo(50) // Slow down by 50ms to make actions visible
                    .setTimeout(60000)); // 60 second timeout for browser to start
                log.info("Successfully launched local browser");
            }
            browserInitialized = true;
        } catch (Exception e) {
            log.error("Failed to initialize browser connection - scraping will not be available", e);
            // Don't throw - just log the error and allow app to continue
            browserInitialized = false;
        }
    }

    public boolean isBrowserAvailable() {
        return browserInitialized && browser != null;
    }

    public Page createStealthPage() {
        // Initialize browser if not already done
        if (!browserInitialized) {
            initializeBrowser();
        }

        if (!isBrowserAvailable()) {
            throw new RuntimeException("Browser is not available - cannot create page");
        }

        try {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setUserAgent(config.getScraper().getUserAgent())
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("America/New_York")
                .setPermissions(Arrays.asList("geolocation"))
                .setGeolocation(40.7128, -74.0060) // New York coordinates
                .setExtraHTTPHeaders(java.util.Map.of(
                    "Accept-Language", "en-US,en;q=0.9",
                    "Accept-Encoding", "gzip, deflate, br",
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                    // NOTE: Removed "Upgrade-Insecure-Requests" - it causes CORS failures with Socket.IO endpoints
                ));

            // When using Browserless CDP, we need to ensure WebSocket connections work
            // This is critical for sites like HLTV that use Socket.IO for live updates
            if (config.getBrowserless().isEnabled()) {
                log.info("Configuring browser context for Browserless with WebSocket support");
                // Note: CDP may still block some WebSocket connections despite these settings
            }

            BrowserContext context = browser.newContext(contextOptions);

            Page page = context.newPage();

            // Enable console logging to catch WebSocket errors
            page.onConsoleMessage(msg -> {
                log.debug("Browser console [{}]: {}", msg.type(), msg.text());
            });

            // Log page errors (including WebSocket failures)
            page.onPageError(exception -> {
                log.error("Browser page error: {}", exception);
            });

            // Inject anti-detection scripts
            page.addInitScript(getStealthScript());

            return page;
        } catch (Exception e) {
            log.error("Failed to create stealth page", e);
            throw new RuntimeException("Failed to create browser page", e);
        }
    }

    private String getStealthScript() {
        return """
            // Overwrite the `languages` property to use a custom getter.
            Object.defineProperty(navigator, 'languages', {
                get: () => ['en-US', 'en']
            });

            // Overwrite the `webdriver` property to return false
            Object.defineProperty(navigator, 'webdriver', {
                get: () => false
            });

            // Mock chrome runtime
            if (!window.chrome) {
                window.chrome = {
                    runtime: {}
                };
            }

            // Overwrite permissions query
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => (
                parameters.name === 'notifications' ?
                    Promise.resolve({ state: Notification.permission }) :
                    originalQuery(parameters)
            );

            // Remove automation indicators
            delete navigator.__proto__.webdriver;

            // Mock plugins (single definition, avoid conflicts)
            Object.defineProperty(navigator, 'plugins', {
                get: () => {
                    return {
                        0: {
                            description: "Portable Document Format",
                            filename: "internal-pdf-viewer",
                            name: "Chrome PDF Plugin"
                        },
                        length: 1
                    };
                }
            });
            """;
    }

    @PreDestroy
    public void cleanup() {
        isShuttingDown = true;
        log.info("Cleaning up browser resources");

        // Give ongoing operations a moment to complete
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Error closing browser", e);
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("Error closing playwright", e);
            }
        }
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }
}