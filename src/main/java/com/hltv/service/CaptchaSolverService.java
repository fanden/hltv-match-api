package com.hltv.service;

import com.hltv.config.ScraperConfig;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CaptchaSolverService {

    private final ScraperConfig config;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public CaptchaSolverService(ScraperConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Solves reCAPTCHA v2 using 2captcha service
     */
    public boolean solveRecaptchaV2(Page page, String siteKey) {
        if (!config.getCaptcha().isEnabled()) {
            log.info("Captcha solving is disabled");
            return false;
        }

        try {
            String pageUrl = page.url();
            log.info("Solving reCAPTCHA v2 for site: {}", pageUrl);

            // Step 1: Submit captcha to 2captcha
            String captchaId = submitRecaptcha(pageUrl, siteKey);
            if (captchaId == null) {
                log.error("Failed to submit captcha to 2captcha");
                return false;
            }

            log.info("Captcha submitted with ID: {}", captchaId);

            // Step 2: Poll for solution (can take 10-80 seconds)
            String solution = pollForSolution(captchaId);
            if (solution == null) {
                log.error("Failed to get captcha solution");
                return false;
            }

            log.info("Captcha solved successfully");

            // Step 3: Inject solution into page
            return injectSolution(page, solution);

        } catch (Exception e) {
            log.error("Error solving captcha", e);
            return false;
        }
    }

    /**
     * Solves Cloudflare Turnstile using 2captcha service
     */
    public boolean solveTurnstile(Page page, String siteKey) {
        if (!config.getCaptcha().isEnabled()) {
            log.info("Captcha solving is disabled");
            return false;
        }

        try {
            String pageUrl = page.url();
            log.info("Solving Cloudflare Turnstile for site: {}", pageUrl);

            // Submit turnstile challenge
            String captchaId = submitTurnstile(pageUrl, siteKey);
            if (captchaId == null) {
                log.error("Failed to submit turnstile to 2captcha");
                return false;
            }

            log.info("Turnstile submitted with ID: {}", captchaId);

            // Poll for solution
            String solution = pollForSolution(captchaId);
            if (solution == null) {
                log.error("Failed to get turnstile solution");
                return false;
            }

            log.info("Turnstile solved successfully");

            // Inject solution
            return injectTurnstileSolution(page, solution);

        } catch (Exception e) {
            log.error("Error solving turnstile", e);
            return false;
        }
    }

    private String submitRecaptcha(String pageUrl, String siteKey) {
        try {
            HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("2captcha.com")
                .addPathSegment("in.php")
                .addQueryParameter("key", config.getCaptcha().getTwocaptchaApiKey())
                .addQueryParameter("method", "userrecaptcha")
                .addQueryParameter("googlekey", siteKey)
                .addQueryParameter("pageurl", pageUrl)
                .addQueryParameter("json", "1")
                .build();

            Request request = new Request.Builder()
                .url(url)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Failed to submit captcha: {}", response.code());
                    return null;
                }

                String responseBody = response.body().string();
                JsonNode json = objectMapper.readTree(responseBody);

                if (json.get("status").asInt() == 1) {
                    return json.get("request").asText();
                } else {
                    log.error("2captcha error: {}", json.get("request").asText());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error submitting captcha", e);
            return null;
        }
    }

    private String submitTurnstile(String pageUrl, String siteKey) {
        try {
            HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("2captcha.com")
                .addPathSegment("in.php")
                .addQueryParameter("key", config.getCaptcha().getTwocaptchaApiKey())
                .addQueryParameter("method", "turnstile")
                .addQueryParameter("sitekey", siteKey)
                .addQueryParameter("pageurl", pageUrl)
                .addQueryParameter("json", "1")
                .build();

            Request request = new Request.Builder()
                .url(url)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Failed to submit turnstile: {}", response.code());
                    return null;
                }

                String responseBody = response.body().string();
                JsonNode json = objectMapper.readTree(responseBody);

                if (json.get("status").asInt() == 1) {
                    return json.get("request").asText();
                } else {
                    log.error("2captcha error: {}", json.get("request").asText());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error submitting turnstile", e);
            return null;
        }
    }

    private String pollForSolution(String captchaId) {
        int maxAttempts = 60; // 60 attempts with 3 second delay = 3 minutes max
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                Thread.sleep(3000); // Wait 3 seconds between checks

                HttpUrl url = new HttpUrl.Builder()
                    .scheme("https")
                    .host("2captcha.com")
                    .addPathSegment("res.php")
                    .addQueryParameter("key", config.getCaptcha().getTwocaptchaApiKey())
                    .addQueryParameter("action", "get")
                    .addQueryParameter("id", captchaId)
                    .addQueryParameter("json", "1")
                    .build();

                Request request = new Request.Builder()
                    .url(url)
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        log.warn("Failed to check captcha status: {}", response.code());
                        attempt++;
                        continue;
                    }

                    String responseBody = response.body().string();
                    JsonNode json = objectMapper.readTree(responseBody);

                    if (json.get("status").asInt() == 1) {
                        return json.get("request").asText();
                    } else {
                        String status = json.get("request").asText();
                        if (!"CAPCHA_NOT_READY".equals(status)) {
                            log.error("2captcha error: {}", status);
                            return null;
                        }
                    }
                }

                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.warn("Error polling for solution", e);
                attempt++;
            }
        }

        log.error("Timeout waiting for captcha solution");
        return null;
    }

    private boolean injectSolution(Page page, String solution) {
        try {
            // Inject the reCAPTCHA response token
            page.evaluate("""
                (token) => {
                    // Find the reCAPTCHA response textarea
                    const textarea = document.querySelector('#g-recaptcha-response, [name="g-recaptcha-response"]');
                    if (textarea) {
                        textarea.value = token;
                        textarea.innerHTML = token;
                    }

                    // Try to trigger callback if it exists
                    if (window.grecaptcha && window.grecaptcha.getResponse) {
                        try {
                            const callback = window.___grecaptcha_cfg?.clients?.[0]?.callback;
                            if (callback && typeof callback === 'function') {
                                callback(token);
                            }
                        } catch (e) {
                            console.log('Could not trigger callback:', e);
                        }
                    }

                    return true;
                }
                """, solution);

            log.info("Injected reCAPTCHA solution into page");
            Thread.sleep(2000); // Wait for any callbacks to execute

            return true;
        } catch (Exception e) {
            log.error("Error injecting solution", e);
            return false;
        }
    }

    private boolean injectTurnstileSolution(Page page, String solution) {
        try {
            // Inject the Turnstile response token
            page.evaluate("""
                (token) => {
                    // Find Turnstile response input
                    const input = document.querySelector('[name="cf-turnstile-response"]');
                    if (input) {
                        input.value = token;
                    }

                    // Trigger turnstile callback if exists
                    if (window.turnstile && window.turnstile.render) {
                        try {
                            const callback = window.turnstileCallback;
                            if (callback && typeof callback === 'function') {
                                callback(token);
                            }
                        } catch (e) {
                            console.log('Could not trigger turnstile callback:', e);
                        }
                    }

                    return true;
                }
                """, solution);

            log.info("Injected Turnstile solution into page");
            Thread.sleep(2000);

            return true;
        } catch (Exception e) {
            log.error("Error injecting turnstile solution", e);
            return false;
        }
    }

    /**
     * Detects and extracts the site key for reCAPTCHA
     */
    public String detectRecaptchaSiteKey(Page page) {
        try {
            Object result = page.evaluate("""
                () => {
                    // Try to find reCAPTCHA site key
                    const recaptchaDiv = document.querySelector('.g-recaptcha, [class*="g-recaptcha"]');
                    if (recaptchaDiv) {
                        const sitekey = recaptchaDiv.getAttribute('data-sitekey');
                        if (sitekey) return sitekey;
                    }

                    // Try all divs with data-sitekey
                    const allDivs = document.querySelectorAll('[data-sitekey]');
                    if (allDivs.length > 0) {
                        return allDivs[0].getAttribute('data-sitekey');
                    }

                    // Try to extract from iframe
                    const iframe = document.querySelector('iframe[src*="google.com/recaptcha"], iframe[src*="recaptcha"]');
                    if (iframe && iframe.src) {
                        const match = iframe.src.match(/[?&]k=([^&]+)/);
                        if (match) return match[1];
                    }

                    // Check window config
                    if (typeof grecaptcha !== 'undefined' && window.___grecaptcha_cfg) {
                        const clients = window.___grecaptcha_cfg.clients;
                        if (clients) {
                            for (let key in clients) {
                                const client = clients[key];
                                if (client && client.sitekey) return client.sitekey;
                            }
                        }
                    }

                    // Try to find in page source/scripts
                    const scripts = document.querySelectorAll('script');
                    for (let script of scripts) {
                        const content = script.textContent || script.innerHTML;
                        const match = content.match(/['"](6[A-Za-z0-9_-]{39})['"]|sitekey['":\s]+['"]?(6[A-Za-z0-9_-]{39})['"]?/);
                        if (match) return match[1] || match[2];
                    }

                    return null;
                }
                """);

            if (result != null && !result.toString().equals("null")) {
                log.info("Detected reCAPTCHA site key: {}", result);
                return result.toString();
            }
            return null;
        } catch (Exception e) {
            log.warn("Error detecting recaptcha site key", e);
            return null;
        }
    }

    /**
     * Detects and extracts the site key for Cloudflare Turnstile
     */
    public String detectTurnstileSiteKey(Page page) {
        try {
            Object result = page.evaluate("""
                () => {
                    // Method 1: Check for data-sitekey attribute
                    const elementsWithSitekey = document.querySelectorAll('[data-sitekey]');
                    for (let el of elementsWithSitekey) {
                        const sitekey = el.getAttribute('data-sitekey');
                        if (sitekey && sitekey.startsWith('0x')) {
                            return sitekey;
                        }
                    }

                    // Method 2: Check iframe src for sitekey parameter
                    const iframes = document.querySelectorAll('iframe');
                    for (let iframe of iframes) {
                        try {
                            const src = iframe.src || '';
                            if (src.includes('turnstile') || src.includes('cloudflare')) {
                                const url = new URL(src);
                                const sitekey = url.searchParams.get('sitekey') || url.searchParams.get('k');
                                if (sitekey) return sitekey;
                            }
                        } catch (e) {
                            // Invalid URL, skip
                        }
                    }

                    // Method 3: Check for Cloudflare challenge form with turnstile response
                    const cfChallenge = document.querySelector('#challenge-form, .cf-challenge-running');
                    if (cfChallenge) {
                        const input = document.querySelector('[name="cf-turnstile-response"]');
                        if (input) {
                            const container = input.closest('[data-sitekey]');
                            if (container) return container.getAttribute('data-sitekey');
                        }
                    }

                    return null;
                }
                """);

            String siteKey = result != null ? result.toString() : null;
            if (siteKey != null && !siteKey.equals("null")) {
                log.info("Detected Turnstile site key: {}", siteKey);
                return siteKey;
            }
            return null;
        } catch (Exception e) {
            log.warn("Error detecting turnstile site key", e);
            return null;
        }
    }
}