package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class RoutingServiceApp {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/route", ctx -> {
            String origin = ctx.queryParam("origin");
            String destination = ctx.queryParam("destination");
            if (origin == null || destination == null) {
                ctx.status(400).result("Both origin and destination are required");
                return;
            }
            route(ctx, origin, destination);
        });

        app.get("/route/{origin}/{destination}", ctx -> {
            route(ctx, ctx.pathParam("origin"), ctx.pathParam("destination"));
        });
    }

    private static void route(io.javalin.http.Context ctx, String origin, String destination) {
        try {
            Map<String, Object> originRecord = lookupIntersection(origin);
            Map<String, Object> destinationRecord = lookupIntersection(destination);
            if (originRecord == null || destinationRecord == null) {
                ctx.status(404).result(toJson(Map.of("error", "Unknown intersection", "origin", origin, "destination", destination))).contentType("application/json");
                return;
            }

            int congestionLevel = fetchCongestionLevel();
            int travelTimeMinutes = 12 + (congestionLevel * 8) + (origin.equalsIgnoreCase(destination) ? 5 : 0);

            ctx.result(toJson(Map.of(
                    "origin", origin,
                    "destination", destination,
                    "congestionLevel", congestionLevel,
                    "travelTimeMinutes", travelTimeMinutes,
                    "valid", true
            ))).contentType("application/json");
        } catch (IllegalStateException e) {
            ctx.status(503).result(toJson(Map.of("error", e.getMessage()))).contentType("application/json");
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }

    private static Map<String, Object> lookupIntersection(String id) {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:7021/intersections/" + id))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() == 200) {
                return MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            }
            return null;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach intersection-service", e);
        }
    }

    private static int fetchCongestionLevel() {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:7022/congestion"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() == 200) {
                Map<String, Object> payload = MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
                Object level = payload.get("level");
                return Integer.parseInt(String.valueOf(level));
            }
            throw new IllegalStateException("Congestion service unavailable");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach congestion-service", e);
        }
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
