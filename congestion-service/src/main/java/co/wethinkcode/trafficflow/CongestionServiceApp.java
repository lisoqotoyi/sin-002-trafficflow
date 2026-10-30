package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CongestionServiceApp {

    private static final AtomicInteger CONGESTION_LEVEL = new AtomicInteger(3);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/congestion", ctx -> {
            ctx.result(toJson(Map.of("level", CONGESTION_LEVEL.get()))).contentType("application/json");
        });

        app.post("/congestion", ctx -> {
            Map<String, Object> payload = MAPPER.readValue(ctx.body(), Map.class);
            Integer requestedLevel = (Integer) payload.getOrDefault("level", CONGESTION_LEVEL.get());
            if (requestedLevel == null) {
                ctx.status(400).result("Level is required");
                return;
            }
            int normalized = Math.max(0, Math.min(8, requestedLevel));
            CONGESTION_LEVEL.set(normalized);
            ctx.result(toJson(Map.of("level", normalized))).contentType("application/json");
        });

        app.put("/congestion", ctx -> {
            try {
                int requestedLevel = Integer.parseInt(ctx.queryParam("level"));
                int normalized = Math.max(0, Math.min(8, requestedLevel));
                CONGESTION_LEVEL.set(normalized);
                ctx.result(toJson(Map.of("level", normalized))).contentType("application/json");
            } catch (NumberFormatException e) {
                ctx.status(400).result("Query parameter 'level' must be an integer between 0 and 8");
            }
        });
    }

    public static int getCurrentLevel() {
        return CONGESTION_LEVEL.get();
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
