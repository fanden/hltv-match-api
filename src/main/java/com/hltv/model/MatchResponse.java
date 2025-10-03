package com.hltv.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class MatchResponse {
    private List<Match> liveMatches;
    private List<Match> upcomingMatches;
}