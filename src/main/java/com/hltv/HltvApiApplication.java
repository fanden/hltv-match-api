package com.hltv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HltvApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HltvApiApplication.class, args);
    }
}