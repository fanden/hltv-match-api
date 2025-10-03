# HLTV Match Data Scraper API

A Spring Boot application that scrapes live and upcoming CS:GO match data from HLTV.org using Browserless for headless Chrome automation.

## Features

- **Captcha Bypass**: Advanced anti-detection techniques to bypass HLTV's bot protection
- **Live Score Updates**: WebSocket interception to capture real-time score updates
- **Persistent Sessions**: Keeps browser sessions alive for 30±5 minutes to collect live data
- **REST API**: Simple endpoints to access live and upcoming match data
- **Dockerized Browser**: Uses Browserless.io for scalable, containerized browser automation

## Architecture

### Captcha Bypass Strategy

The application implements multiple layers of anti-detection:

1. **Browser Fingerprinting**:
   - Custom user agent from real Chrome browser
   - Proper viewport size (1920x1080)
   - Geolocation (New York)
   - Timezone and locale settings
   - Realistic HTTP headers

2. **JavaScript Anti-Detection**:
   - Overwrites `navigator.webdriver` to return `false`
   - Mocks browser plugins
   - Adds Chrome runtime objects
   - Removes automation indicators

3. **Behavioral Mimicking**:
   - Random mouse movements
   - Random scrolling
   - Natural delays between actions
   - Human-like navigation patterns

4. **Cloudflare Challenge Handling**:
   - Waits for automatic challenge completion
   - Multiple retry strategies
   - Exponential backoff on failures

## Setup

### Prerequisites

- Java 17+
- Gradle 8.5+ (included via wrapper)
- Docker & Docker Compose

### Installation

1. Start Browserless container:
```bash
docker-compose up -d
```

2. Install Playwright browsers (first time only):
```bash
./gradlew exec -PmainClass=com.microsoft.playwright.CLI -Pargs="install chromium"
# Or manually: java -cp $(./gradlew printClasspath -q) com.microsoft.playwright.CLI install chromium
```

3. Build the application:
```bash
./gradlew build
```

4. Run the application:
```bash
./gradlew bootRun
```

The API will be available at `http://localhost:8080`

## API Endpoints

### Get All Matches
```bash
GET /api/matches
```

Returns both live and upcoming matches:
```json
{
  "liveMatches": [
    {
      "matchId": "2366848",
      "team1Name": "Vitality",
      "team2Name": "FaZe",
      "team1Score": 13,
      "team2Score": 10,
      "format": "bo3",
      "event": "IEM Katowice 2024",
      "isLive": true,
      "matchUrl": "https://www.hltv.org/matches/2366848/...",
      "lastUpdated": "2024-01-15T14:30:00"
    }
  ],
  "upcomingMatches": [...]
}
```

### Get Live Matches Only
```bash
GET /api/matches/live
```

### Get Upcoming Matches Only
```bash
GET /api/matches/upcoming
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
hltv:
  browserless:
    url: ws://localhost:3000  # Browserless WebSocket URL
  scraper:
    page-timeout: 1800000      # 30 minutes in ms
    random-variance: 300000    # +/- 5 minutes in ms
    user-agent: "Mozilla/5.0 ..."
```

## How It Works

1. **Initialization**: On startup, the scraper creates a stealth browser context with anti-detection scripts
2. **Navigation**: Navigates to hltv.org/matches with retry logic and random delays
3. **Captcha Handling**: Detects and attempts to bypass captchas using multiple strategies
4. **Data Extraction**: Parses match data from the DOM using JavaScript evaluation
5. **WebSocket Monitoring**: Intercepts WebSocket connections to capture live score updates
6. **Session Persistence**: Keeps the page alive for 30±5 minutes to collect real-time data
7. **Scheduled Re-scraping**: Automatically restarts scraping every 30 minutes

## Captcha Bypass Details

The most critical component is bypassing HLTV's protection (likely Cloudflare):

1. **Stealth Fingerprint**: The browser is configured to look like a real Chrome instance
2. **Script Injection**: Anti-detection scripts run before page load
3. **Wait Strategy**: Allows time for automatic challenge resolution (5-10 seconds for Cloudflare)
4. **Human Simulation**: Mimics human behavior with mouse movements and scrolling
5. **Retry Logic**: Multiple attempts with exponential backoff

Success rate depends on:
- Quality of browser fingerprint
- IP reputation
- Request frequency
- HLTV's current protection level

## Troubleshooting

### Captcha Not Bypassing

- Check Browserless logs: `docker-compose logs -f browserless`
- Verify browser fingerprint is realistic
- Try using residential proxy (not implemented by default)
- Increase wait times in `handleCaptcha()` method

### No Data Returned

- Check application logs for parsing errors
- HLTV may have changed their HTML structure
- Update selectors in `parseMatches()` method

### Browser Connection Issues

- Ensure Browserless container is running: `docker ps`
- Check Browserless is accessible: `curl http://localhost:3000`
- Verify WebSocket URL in application.yml

## Development

### Project Structure

```
src/main/java/com/hltv/
├── HltvApiApplication.java          # Main application
├── config/
│   └── ScraperConfig.java           # Configuration properties
├── controller/
│   └── MatchController.java         # REST endpoints
├── model/
│   ├── Match.java                   # Match data model
│   └── MatchResponse.java           # API response model
└── service/
    ├── BrowserService.java          # Browser management & stealth
    └── HltvScraperService.java      # Scraping logic
```

### Key Classes

- **BrowserService**: Manages Playwright connection to Browserless, configures stealth mode
- **HltvScraperService**: Core scraping logic, captcha handling, WebSocket interception
- **MatchController**: REST API endpoints

## License

MIT