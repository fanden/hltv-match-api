# HLTV API

REST API for retrieving live and upcoming Counter-Strike 2 match data from HLTV.org.

## Quick Start

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Gradle (included via wrapper)

### Installation

1. Clone the repository and navigate to the project directory

2. Start the Browserless container:
```bash
docker-compose up -d
```

3. Install Playwright browsers (first time only):
```bash
./gradlew exec -PmainClass=com.microsoft.playwright.CLI -Pargs="install chromium"
```

4. Build and run:
```bash
./gradlew build
./gradlew bootRun
```

The API will be available at `http://localhost:8080`

## API Documentation

### Endpoints

#### Get All Matches
```
GET /api/matches
```

Returns both live and upcoming matches.

**Response:**
```json
{
  "liveMatches": [
    {
      "matchId": "2366848",
      "team1Name": "Vitality",
      "team2Name": "FaZe",
      "team1Logo": "https://img-cdn.hltv.org/teamlogo/...",
      "team2Logo": "https://img-cdn.hltv.org/teamlogo/...",
      "team1Score": 13,
      "team2Score": 10,
      "team1MapWins": 1,
      "team2MapWins": 0,
      "format": "bo3",
      "event": "IEM Katowice 2024",
      "isLive": true,
      "matchTime": "2024-10-03T14:30:00",
      "matchUrl": "https://www.hltv.org/matches/2366848/vitality-vs-faze",
      "lastUpdated": "2024-10-03T14:35:00"
    }
  ],
  "upcomingMatches": [
    {
      "matchId": "2366849",
      "team1Name": "NAVI",
      "team2Name": "G2",
      "team1Logo": "https://img-cdn.hltv.org/teamlogo/...",
      "team2Logo": "https://img-cdn.hltv.org/teamlogo/...",
      "team1Score": null,
      "team2Score": null,
      "team1MapWins": null,
      "team2MapWins": null,
      "format": "bo3",
      "event": "IEM Katowice 2024",
      "isLive": false,
      "matchTime": "2024-10-03T16:00:00",
      "matchUrl": "https://www.hltv.org/matches/2366849/navi-vs-g2",
      "lastUpdated": "2024-10-03T14:35:00"
    }
  ]
}
```

#### Get Live Matches Only
```
GET /api/matches/live
```

Returns only currently live matches.

#### Get Upcoming Matches Only
```
GET /api/matches/upcoming
```

Returns only upcoming matches.

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| matchId | string | Unique match identifier |
| team1Name | string | First team name |
| team2Name | string | Second team name |
| team1Logo | string | URL to first team's logo |
| team2Logo | string | URL to second team's logo |
| team1Score | integer | Current round score for team 1 (null if not live) |
| team2Score | integer | Current round score for team 2 (null if not live) |
| team1MapWins | integer | Number of maps won by team 1 (null if not live) |
| team2MapWins | integer | Number of maps won by team 2 (null if not live) |
| format | string | Match format (e.g., "bo3", "bo1") |
| event | string | Tournament or event name |
| isLive | boolean | Whether the match is currently live |
| matchTime | string | ISO 8601 timestamp of match start time |
| matchUrl | string | Direct URL to the match page on HLTV |
| lastUpdated | string | ISO 8601 timestamp of last data update |

## Configuration

Edit `src/main/resources/application.yml` to customize settings:

```yaml
server:
  port: 8080

hltv:
  browserless:
    url: ws://localhost:3000
    enabled: true
  scraper:
    page-timeout: 1800000      # Session duration in milliseconds (30 minutes)
    random-variance: 300000    # Random variance for session duration (+/- 5 minutes)
    user-agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  captcha:
    twocaptcha-api-key: ${TWOCAPTCHA_API_KEY}
    enabled: true
```

### Environment Variables

- `TWOCAPTCHA_API_KEY` - API key for 2Captcha service (optional)

Set via command line:
```bash
export TWOCAPTCHA_API_KEY="your-api-key"
```

## How It Works

The application uses Playwright to automate a headless Chrome browser and scrape match data from HLTV.org. The scraper:

1. Launches a browser session via Browserless
2. Navigates to the HLTV matches page
3. Parses live and upcoming match data from the page
4. Maintains the session for approximately 30 minutes to collect live score updates
5. Automatically refreshes data when matches are starting
6. Re-scrapes every 30 minutes to keep data fresh

## Project Structure

```
src/main/java/com/hltv/
├── HltvApiApplication.java      # Main application entry point
├── config/
│   └── ScraperConfig.java       # Configuration properties binding
├── controller/
│   └── MatchController.java     # REST API endpoints
├── model/
│   ├── Match.java               # Match data model
│   └── MatchResponse.java       # API response wrapper
└── service/
    ├── BrowserService.java      # Browser automation setup
    ├── CaptchaSolverService.java # Captcha handling
    └── HltvScraperService.java  # Data scraping logic
```

## Troubleshooting

### No matches returned

Check the application logs for errors. HLTV may have changed their page structure, requiring updates to the parsing logic in `HltvScraperService.java`.

### Browser connection errors

Ensure the Browserless container is running:
```bash
docker ps
docker-compose logs browserless
```

Restart if needed:
```bash
docker-compose restart browserless
```

### Application won't start

Verify Java version:
```bash
java -version  # Should be 17 or higher
```

Check if port 8080 is available:
```bash
lsof -i :8080
```

## Development

### Build
```bash
./gradlew build
```

### Run tests
```bash
./gradlew test
```

### Run with debug logging
Add to `application.yml`:
```yaml
logging:
  level:
    com.hltv: DEBUG
```

## Tech Stack

- **Spring Boot 3.2.0** - Web framework
- **Playwright 1.40.0** - Browser automation
- **Browserless** - Headless Chrome container
- **2Captcha API** - Captcha solving service
- **Jackson** - JSON parsing
- **Lombok** - Boilerplate reduction

## License

MIT
