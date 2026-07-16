package com.fleet.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CanIngestionApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanIngestionApplication.class, args);
    }
}
