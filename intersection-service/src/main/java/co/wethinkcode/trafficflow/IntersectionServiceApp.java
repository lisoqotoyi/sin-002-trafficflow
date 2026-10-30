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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IntersectionServiceApp {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<Map<String, Object>> FALLBACK_INTERSECTIONS = List.of(
            mapOf("INT-1001", "Downtown", "4-way", true),
            mapOf("INT-1002", "Midtown", "pedestrian", true),
            mapOf("INT-1003", "Downtown", "4-way", false),
            mapOf("INT-1004", "Uptown", null, true),
            mapOf("INT-1005", "Downtown", "roundabout", true),
            mapOf("INT-1006", "Midtown", "4-way", false),
            mapOf("INT-1007", "Eastside", null, true),
            mapOf("INT-1008", "Uptown", "pedestrian", false),
            mapOf("INT-1009", "Westside", "4-way", false),
            mapOf("INT-1010", "Downtown", "stop-sign", true),
            mapOf("INT-1011", "Midtown", "4-way", true),
            mapOf("INT-1012", "Eastside", "roundabout", false),
            mapOf("INT-1013", "Westside", null, null),
            mapOf("INT-1014", "Uptown", "pedestrian", true),
            mapOf("INT-1015", null, "4-way", true),
            mapOf("INT-1016", "Downtown", "4-way", true),
            mapOf("INT-1017", "Eastside", "stop-sign", false)
    );

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/intersections", ctx -> {
            List<Map<String, Object>> intersections = loadIntersections();
            ctx.result(toJson(intersections)).contentType("application/json");
        });

        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Map<String, Object> record = findIntersectionById(id);
            if (record == null) {
                ctx.status(404).result("Intersection not found");
                return;
            }
            ctx.result(toJson(record)).contentType("application/json");
        });

        app.get("/districts/{district}", ctx -> {
            String district = ctx.pathParam("district");
            List<Map<String, Object>> matches = new ArrayList<>();
            for (Map<String, Object> record : loadIntersections()) {
                Object districtValue = record.get("district");
                if (districtValue != null && districtValue.toString().equalsIgnoreCase(district)) {
                    matches.add(record);
                }
            }
            if (matches.isEmpty()) {
                ctx.status(404).result("District not found");
                return;
            }
            ctx.result(toJson(matches)).contentType("application/json");
        });
    }

    public static List<Map<String, Object>> loadIntersections() {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:7020/intersections"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return MAPPER.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (IOException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return FALLBACK_INTERSECTIONS;
    }

    public static Map<String, Object> findIntersectionById(String id) {
        if (id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        for (Map<String, Object> record : loadIntersections()) {
            String recordId = normalizeId(String.valueOf(record.get("id")));
            if (normalized.equalsIgnoreCase(recordId)) {
                return record;
            }
        }
        return null;
    }

    public static boolean isKnownIntersection(String id) {
        return findIntersectionById(id) != null;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return cleaned.replace(' ', '-');
    }

    private static Map<String, Object> mapOf(String id, String district, String signalType, Boolean active) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("district", district);
        record.put("signalType", signalType);
        record.put("active", active);
        return record;
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }
}

// MQ TODO: publishes a periodic heartbeat to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at
// MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig), consumed by intersection-watchdog.
