package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class IntersectionWatchdogApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicBoolean ALERT_ACTIVE = new AtomicBoolean(false);

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7024);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alert", ctx -> ctx.result(toJson(Map.of("alertActive", ALERT_ACTIVE.get()))).contentType("application/json"));
        app.post("/alert/clear", ctx -> {
            ALERT_ACTIVE.set(false);
            ctx.result(toJson(Map.of("alertActive", false))).contentType("application/json");
        });

        app.post("/alert/trigger", ctx -> {
            ALERT_ACTIVE.set(true);
            ctx.result(toJson(Map.of("alertActive", true))).contentType("application/json");
        });
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }
}

// MQ TODO: subscribes to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at MqConfig.BROKER_URL
// (see co.wethinkcode.trafficflow.mq.MqConfig) and alerts if a heartbeat from
// intersection-service is missed or a message lands in the dead-letter queue.
