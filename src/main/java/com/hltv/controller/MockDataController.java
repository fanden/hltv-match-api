package com.hltv.controller;

import com.hltv.model.Match;
import com.hltv.model.MatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock data controller for offline development and testing.
 * Returns static match data without requiring the scraper service.
 */
@RestController
@RequestMapping("/api/mock")
@Slf4j
public class MockDataController {

    @GetMapping("/matches")
    public ResponseEntity<MatchResponse> getMockMatches() {
        log.debug("Fetching mock match data");

        MatchResponse response = MatchResponse.builder()
            .liveMatches(getMockLiveMatches())
            .upcomingMatches(getMockUpcomingMatches())
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/matches/live")
    public ResponseEntity<List<Match>> getLiveMatches() {
        log.debug("Fetching mock live matches");
        return ResponseEntity.ok(getMockLiveMatches());
    }

    @GetMapping("/matches/upcoming")
    public ResponseEntity<List<Match>> getUpcomingMatches() {
        log.debug("Fetching mock upcoming matches");
        return ResponseEntity.ok(getMockUpcomingMatches());
    }

    private List<Match> getMockLiveMatches() {
        List<Match> liveMatches = new ArrayList<>();

        liveMatches.add(Match.builder()
            .matchId("2386680")
            .team1Name("Players")
            .team2Name("UnderDogs")
            .team1Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=P")
            .team2Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=U")
            .team1Score(3)
            .team2Score(8)
            .team1MapWins(0)
            .team2MapWins(0)
            .format("Live")
            .event("CCT Season 3 South America Series 5")
            .matchTime(null)
            .matchUrl("https://www.hltv.org/matches/2386680/players-vs-underdogs-cct-season-3-south-america-series-5")
            .lastUpdated(LocalDateTime.now())
            .isLive(true)
            .build());

        liveMatches.add(Match.builder()
            .matchId("2386525")
            .team1Name("Just Swing")
            .team2Name("Kaleido")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/GNiCfDu3aX3HIarMXK7faV.png?ixlib=java-2.1.0&w=50&s=0e0a52450aa10dc71f9f72e87272087b")
            .team2Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=K")
            .team1Score(2)
            .team2Score(9)
            .team1MapWins(0)
            .team2MapWins(0)
            .format("Live")
            .event("ESL Challenger League Season 50 Asia-Pacific Cup 3")
            .matchTime(null)
            .matchUrl("https://www.hltv.org/matches/2386525/just-swing-vs-kaleido-esl-challenger-league-season-50-asia-pacific-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(true)
            .build());

        liveMatches.add(Match.builder()
            .matchId("2386722")
            .team1Name("kONO")
            .team2Name("FORZE Reload")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/rW5srPjiZnkwa3TlC4vJFi.png?ixlib=java-2.1.0&w=50&s=1b6453d6089b4ea66937a105536973b6")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/YJnWBDwmRDJ9o2uOUrhNeQ.png?ixlib=java-2.1.0&w=50&s=83f5ce35b3f98833671987bc637c74b7")
            .team1Score(3)
            .team2Score(1)
            .team1MapWins(1)
            .team2MapWins(1)
            .format("Live")
            .event("NODWIN Clutch Series 1")
            .matchTime(null)
            .matchUrl("https://www.hltv.org/matches/2386722/kono-vs-forze-reload-nodwin-clutch-series-1")
            .lastUpdated(LocalDateTime.now())
            .isLive(true)
            .build());

        liveMatches.add(Match.builder()
            .matchId("2386417")
            .team1Name("Sangal")
            .team2Name("JiJieHao")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/zPv_FeMF8CANC10Jz32P9l.png?ixlib=java-2.1.0&w=50&s=741a13d27b484b39f24cdc76dbf80568")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/OmcJcjX0obWnKkP7ChpuFA.png?ixlib=java-2.1.0&w=50&s=8e3bd16625d6f62c95285c98080ded66")
            .team1Score(6)
            .team2Score(7)
            .team1MapWins(1)
            .team2MapWins(1)
            .format("Live")
            .event("Exort The Proving Grounds Season 5")
            .matchTime(null)
            .matchUrl("https://www.hltv.org/matches/2386417/sangal-vs-jijiehao-exort-the-proving-grounds-season-5")
            .lastUpdated(LocalDateTime.now())
            .isLive(true)
            .build());

        liveMatches.add(Match.builder()
            .matchId("2386526")
            .team1Name("DEPO")
            .team2Name("FengDa")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/PEMbDigYIpch-9_wF_JCC9.png?ixlib=java-2.1.0&w=50&s=cbf54c860cf756894b38ed071911437d")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/_Q3iz2MJXmpJp9QGv9jWnc.png?ixlib=java-2.1.0&w=50&s=3436ac6e2bc88b6ededfac64ab9bdcd5")
            .team1Score(7)
            .team2Score(7)
            .team1MapWins(0)
            .team2MapWins(0)
            .format("Live")
            .event("ESL Challenger League Season 50 Asia-Pacific Cup 3")
            .matchTime(null)
            .matchUrl("https://www.hltv.org/matches/2386526/depo-vs-fengda-esl-challenger-league-season-50-asia-pacific-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(true)
            .build());

        liveMatches.add(Match.builder()
            .matchId("2386333")
            .team1Name("Spirit Academy")
            .team2Name("1win")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/txUq00aBKY7O4fLVJegmi_.png?ixlib=java-2.1.0&w=50&s=0be2e166db241569e89ac4ab8f5fb9a2")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/zbmzI2IMFNwHUvTO4EHaTZ.png?ixlib=java-2.1.0&w=50&s=fd29838f5b22b76075de4c80c2e04c0b")
            .team1Score(2)
            .team2Score(6)
            .team1MapWins(0)
            .team2MapWins(1)
            .format("Live")
            .event("CCT Season 3 Europe Series 8")
            .matchTime(null)
            .matchUrl("https://www.hltv.org/matches/2386333/spirit-academy-vs-1win-cct-season-3-europe-series-8")
            .lastUpdated(LocalDateTime.now())
            .isLive(true)
            .build());

        return liveMatches;
    }

    private List<Match> getMockUpcomingMatches() {
        List<Match> upcomingMatches = new ArrayList<>();

        upcomingMatches.add(Match.builder()
            .matchId("2386759")
            .team1Name("Dusty Roses")
            .team2Name("CAPIVARAS")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/PNUX5aK9d4-WkrskZv6qK3.png?ixlib=java-2.1.0&w=50&s=414fd80ef34e35c9dbbe59a695efb2d2")
            .team2Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=C")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 South America")
            .matchTime(LocalDateTime.parse("2025-10-16T23:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386759/dusty-roses-vs-capivaras-esl-impact-league-season-8-south-america")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386756")
            .team1Name("Overpeek")
            .team2Name("BIG EQUIPA")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/dPb1EGDqNC4tk2Z3xQhN-j.png?ixlib=java-2.1.0&w=50&s=f9ff80bbdc0ae1c36fc9ae1c89f8e653")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/6UagLkzIYk5UVlCFmEYXt1.png?ixlib=java-2.1.0&w=50&s=d2655c1d0158cda2f0bdaaeced552421")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 Europe")
            .matchTime(LocalDateTime.parse("2025-10-16T20:30:00"))
            .matchUrl("https://www.hltv.org/matches/2386756/overpeek-vs-big-equipa-esl-impact-league-season-8-europe")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386755")
            .team1Name("Flame Sharks fe")
            .team2Name("888aura")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/7IqEkkdoqF50GFlhfpbb9V.png?ixlib=java-2.1.0&w=50&s=0ab151f3a2ca39ec0ad275c8ce288868")
            .team2Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=8")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 Europe")
            .matchTime(LocalDateTime.parse("2025-10-16T20:30:00"))
            .matchUrl("https://www.hltv.org/matches/2386755/flame-sharks-fe-vs-888aura-esl-impact-league-season-8-europe")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386758")
            .team1Name("Four Magic")
            .team2Name("MIBR fe")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/DYtqTaaDpYj5hVB0aK0glH.png?ixlib=java-2.1.0&w=50&s=769b592b2ba1f55e130b479021187a6b")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/m_JQ624LNFHWiUY-25uuaE.png?invert=true&ixlib=java-2.1.0&sat=-100&w=50&s=6f521aaf8c039fc3f6f235a8a1b7dd40")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 South America")
            .matchTime(LocalDateTime.parse("2025-10-15T23:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386758/four-magic-vs-mibr-fe-esl-impact-league-season-8-south-america")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386559")
            .team1Name("SINNERS")
            .team2Name("Zero Tenacity")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/9l_WdQSU9JsNHzpK-pwOG2.svg?ixlib=java-2.1.0&s=af432c3ef61c0c843331cc0dc2fed1ed")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/c9X-Lf6Fy7xe4yxHwepv8I.png?ixlib=java-2.1.0&w=50&s=1925d78a9d57f34eaf736d5c5a037883")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Challenger League Season 50 Europe Cup 3")
            .matchTime(LocalDateTime.parse("2025-10-10T19:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386559/sinners-vs-zero-tenacity-esl-challenger-league-season-50-europe-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386757")
            .team1Name("FURIA fe")
            .team2Name("Curralzinho")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/cZaGsN8yj6I2dEMfPsibLx.png?ixlib=java-2.1.0&w=50&s=b5481473462f811faf1c535778fcd20d")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/c2fvY2-r6jh1oXvBDGPK3Q.png?invert=true&ixlib=java-2.1.0&sat=-100&w=50&s=9712dd1ee2eaf30ca614d9ba0e39b18c")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 South America")
            .matchTime(LocalDateTime.parse("2025-10-15T23:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386757/furia-fe-vs-curralzinho-esl-impact-league-season-8-south-america")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386554")
            .team1Name("Insilio")
            .team2Name("The Glecs")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/_xqicJ8DEOBw-M74mVqh2-.png?ixlib=java-2.1.0&w=50&s=298cba09fc03af645dc3b8f151b4f849")
            .team2Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=T")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("")
            .matchTime(LocalDateTime.parse("2025-10-09T19:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386554/insilio-vs-the-glecs-esl-challenger-league-season-50-europe-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386598")
            .team1Name("Keyd Stars")
            .team2Name("Dusty Roots")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/m-SA9fWSyBqRgsrDCDXCId.png?ixlib=java-2.1.0&w=50&s=5e9b55327740978fb8f425c27d2ba70a")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/_1BiVpPT6y1w_jVvTSn7nt.png?ixlib=java-2.1.0&w=50&s=35e5c80797870d1893326794e28963a3")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Challenger League Season 50 South America Cup 3")
            .matchTime(LocalDateTime.parse("2025-10-14T23:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386598/keyd-stars-vs-dusty-roots-esl-challenger-league-season-50-south-america-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386553")
            .team1Name("GUN5")
            .team2Name("JiJieHao")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/h7rPBXKkg9rmhbeBVmRG28.png?ixlib=java-2.1.0&w=50&s=691011f74dd8f8a8786a142b7b2b8c8e")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/OmcJcjX0obWnKkP7ChpuFA.png?ixlib=java-2.1.0&w=50&s=8e3bd16625d6f62c95285c98080ded66")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("")
            .matchTime(LocalDateTime.parse("2025-10-09T19:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386553/gun5-vs-jijiehao-esl-challenger-league-season-50-europe-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386597")
            .team1Name("ODDIK")
            .team2Name("RED Canids")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/vpWs2bh24DJ8mqXotmXCJM.png?ixlib=java-2.1.0&w=50&s=415cf2a5a2ef2c318943b5bc53b38681")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/Lyr7VJ-litGOwnRBu_8K5q.png?ixlib=java-2.1.0&w=50&s=4beac26b74d83ad2b1753ba2c5cb575d")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Challenger League Season 50 South America Cup 3")
            .matchTime(LocalDateTime.parse("2025-10-14T23:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386597/oddik-vs-red-canids-esl-challenger-league-season-50-south-america-cup-3")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386754")
            .team1Name("shinigami")
            .team2Name("Imperial Valkyries")
            .team1Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=s")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/Hc-AMHZB9eKXBOJwtNvazg.png?ixlib=java-2.1.0&w=50&s=3878edba54104c915d0b7d2dd8256a39")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 Europe")
            .matchTime(LocalDateTime.parse("2025-10-15T20:30:00"))
            .matchUrl("https://www.hltv.org/matches/2386754/shinigami-vs-imperial-valkyries-esl-impact-league-season-8-europe")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386753")
            .team1Name("hindsight")
            .team2Name("Let Her Cook")
            .team1Logo("https://www.hltv.org/dynamic-svg/teamplaceholder?letter=h")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/jBvdaNrg3-E8gwW_Ws1f6z.png?ixlib=java-2.1.0&w=50&s=11cfd8d22ef7f97ae34903040aef3baa")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("ESL Impact League Season 8 Europe")
            .matchTime(LocalDateTime.parse("2025-10-15T20:30:00"))
            .matchUrl("https://www.hltv.org/matches/2386753/hindsight-vs-let-her-cook-esl-impact-league-season-8-europe")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        upcomingMatches.add(Match.builder()
            .matchId("2386671")
            .team1Name("ECLOT")
            .team2Name("GLORE")
            .team1Logo("https://img-cdn.hltv.org/teamlogo/_hHH1jUhX-4DfQpnVo6Gv-.png?ixlib=java-2.1.0&w=50&s=2c4728b37003e75e724612383c869247")
            .team2Logo("https://img-cdn.hltv.org/teamlogo/6b_9mH_f73AnA4Nk6tAija.png?ixlib=java-2.1.0&w=50&s=abe1b490b5ca767bac92b58f56a9165b")
            .team1Score(null)
            .team2Score(null)
            .team1MapWins(null)
            .team2MapWins(null)
            .format("bo3")
            .event("")
            .matchTime(LocalDateTime.parse("2025-10-09T16:00:00"))
            .matchUrl("https://www.hltv.org/matches/2386671/eclot-vs-glore-slovak-national-championship-2025-finals")
            .lastUpdated(LocalDateTime.now())
            .isLive(false)
            .build());

        return upcomingMatches;
    }
}
