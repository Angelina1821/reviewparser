package com.example.reviewparser.scheduler;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ParserScheduler {

    @Scheduled(fixedRate = 600000)
    public void scheduleTask(){
        System.out.println("Scheduler работает");
    }
}