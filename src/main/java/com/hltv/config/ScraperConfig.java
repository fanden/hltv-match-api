package com.hltv.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hltv")
@Data
public class ScraperConfig {
    private BrowserlessConfig browserless = new BrowserlessConfig();
    private ScraperSettings scraper = new ScraperSettings();
    private CaptchaConfig captcha = new CaptchaConfig();

    @Data
    public static class BrowserlessConfig {
        private String url = "ws://localhost:3000";
        private boolean enabled = true; // Set to false to use local browser with visible window
    }

    @Data
    public static class ScraperSettings {
        private long pageTimeout = 1800000L; // 30 minutes
        private long randomVariance = 300000L; // +/- 5 minutes
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    @Data
    public static class CaptchaConfig {
        private String twocaptchaApiKey;
        private boolean enabled = true;
    }
}