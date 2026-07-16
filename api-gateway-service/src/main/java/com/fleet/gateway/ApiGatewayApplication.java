package com.fleet.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reactive Spring Cloud Gateway application. Acts as the single external entry
 * point for the connected-vehicle platform, routing /api/** traffic to the five
 * backend microservices (see application.yml for the route table).
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
