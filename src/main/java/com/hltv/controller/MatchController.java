package com.hltv.controller;

import com.hltv.model.Match;
import com.hltv.model.MatchResponse;
import com.hltv.service.HltvScraperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@Slf4j
public class MatchController {

    private final HltvScraperService scraperService;

    public MatchController(HltvScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @GetMapping
    public ResponseEntity<MatchResponse> getMatches() {
        log.debug("Fetching all matches");

        List<Match> liveMatches = scraperService.getLiveMatches();
        List<Match> upcomingMatches = scraperService.getUpcomingMatches();

        MatchResponse response = MatchResponse.builder()
            .liveMatches(liveMatches)
            .upcomingMatches(upcomingMatches)
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/live")
    public ResponseEntity<List<Match>> getLiveMatches() {
        log.debug("Fetching live matches");
        return ResponseEntity.ok(scraperService.getLiveMatches());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Match>> getUpcomingMatches() {
        log.debug("Fetching upcoming matches");
        return ResponseEntity.ok(scraperService.getUpcomingMatches());
    }
}