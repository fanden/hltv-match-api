package com.hltv.model;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class Match {
    private String matchId;
    private String team1Name;
    private String team2Name;
    private String team1Logo;
    private String team2Logo;
    private Integer team1Score;      // Current map round score (e.g., 7)
    private Integer team2Score;      // Current map round score (e.g., 10)
    private Integer team1MapWins;    // Maps won in series (e.g., 1 in a BO3)
    private Integer team2MapWins;    // Maps won in series (e.g., 0 in a BO3)
    private String format;
    private String event;
    private LocalDateTime matchTime;
    private boolean isLive;
    private String matchUrl;
    private LocalDateTime lastUpdated;
}