package com.fleet.gateway.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * Purely informational index endpoint. A @RestController works fine on WebFlux
 * (as used by Spring Cloud Gateway); returning a {@link Mono} keeps the handler
 * non-blocking. This does NOT participate in routing — the gateway route table
 * (application.yml) handles /api/** proxying. This only serves "/" so operators
 * can discover the available route prefixes and each backend's Swagger UI.
 */
@RestController
public class IndexController {

    @GetMapping("/")
    public Mono<Map<String, Object>> index() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("ingestion", "/api/ingestion/**");
        routes.put("decoder", "/api/decoder/**");
        routes.put("telemetry", "/api/telemetry/**");
        routes.put("diagnostics", "/api/diagnostics/**");
        routes.put("fleet", "/api/fleet/**");

        // Swagger UIs are exposed directly by each backend on its own port
        // (documented here for developer convenience; not proxied by the gateway).
        Map<String, String> swagger = new LinkedHashMap<>();
        swagger.put("ingestion", "http://localhost:8081/swagger-ui.html");
        swagger.put("decoder", "http://localhost:8082/swagger-ui.html");
        swagger.put("telemetry", "http://localhost:8083/swagger-ui.html");
        swagger.put("diagnostics", "http://localhost:8084/swagger-ui.html");
        swagger.put("fleet", "http://localhost:8085/swagger-ui.html");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "api-gateway-service");
        body.put("description", "Single entry point for the connected-vehicle platform");
        body.put("routes", routes);
        body.put("swagger", swagger);

        return Mono.just(body);
    }
}
