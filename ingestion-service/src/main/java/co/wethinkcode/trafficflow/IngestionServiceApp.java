package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IngestionServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "", "-", "n/a", "na", "nan", "tbd", "unknown", "unknowns"
    );

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/intersections", ctx -> ctx.result(toJson(readCleanedRecords())).contentType("application/json"));
        app.get("/intersections/{id}", ctx -> {
            String requestedId = normalizeId(ctx.pathParam("id"));
            List<Map<String, Object>> records = readCleanedRecords();
            for (Map<String, Object> record : records) {
                if (requestedId.equalsIgnoreCase((String) record.get("id"))) {
                    ctx.result(toJson(record)).contentType("application/json");
                    return;
                }
            }
            ctx.status(404).result("Intersection not found");
        });
    }

    public static List<Map<String, Object>> readCleanedRecords() {
        List<Map<String, Object>> records = new ArrayList<>();
        try (InputStream inputStream = IngestionServiceApp.class.getResourceAsStream("/intersections-legacy.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            if (inputStream == null) {
                return records;
            }

            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                String[] values = line.split(",", -1);
                if (values.length < 4) {
                    continue;
                }

                Map<String, Object> record = normalizeRecord(values[0], values[1], values[2], values[3]);
                if (record == null) {
                    continue;
                }
                records.add(record);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read legacy intersections CSV", e);
        }

        return deduplicateRecords(records);
    }

    private static List<Map<String, Object>> deduplicateRecords(List<Map<String, Object>> input) {
        LinkedHashMap<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> record : input) {
            String id = normalizeId((String) record.get("id"));
            if (id == null || id.isBlank()) {
                continue;
            }

            Map<String, Object> existing = deduped.get(id);
            if (existing == null) {
                deduped.put(id, record);
                continue;
            }

            for (String key : List.of("district", "signalType", "active")) {
                Object candidateValue = record.get(key);
                if (candidateValue == null || (candidateValue instanceof String s && s.isBlank())) {
                    continue;
                }
                Object currentValue = existing.get(key);
                if (currentValue == null || (currentValue instanceof String s && s.isBlank())) {
                    existing.put(key, candidateValue);
                }
            }
            if (existing.get("id") == null) {
                existing.put("id", id);
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private static Map<String, Object> normalizeRecord(String rawId, String rawDistrict, String rawSignalType, String rawActive) {
        String safeId = normalizeId(rawId);
        if (safeId == null || safeId.isBlank()) {
            return null;
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", safeId);

        String district = normalizeDistrict(rawDistrict);
        record.put("district", district);

        String signalType = normalizeSignalType(rawSignalType);
        record.put("signalType", signalType);

        Boolean active = normalizeBoolean(rawActive);
        record.put("active", active);

        return record;
    }

    private static String normalizeId(String value) {
        String cleaned = normalizeText(value);
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }
        return cleaned.toUpperCase(Locale.ROOT).replace(' ', '-');
    }

    private static String normalizeDistrict(String value) {
        String cleaned = normalizeText(value);
        if (cleaned == null || isPlaceholder(cleaned)) {
            return null;
        }

        return ArraysToTitleCase(cleaned);
    }

    private static String normalizeSignalType(String value) {
        String cleaned = normalizeText(value);
        if (cleaned == null || isPlaceholder(cleaned)) {
            return null;
        }

        String normalized = cleaned.toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replaceAll("\\s+", "-");

        if (normalized.equals("4-way") || normalized.equals("4way")) {
            return "4-way";
        }
        if (normalized.equals("stop-sign") || normalized.equals("stop_sign") || normalized.equals("stop sign")) {
            return "stop-sign";
        }
        if (normalized.equals("roundabout")) {
            return "roundabout";
        }
        if (normalized.equals("pedestrian")) {
            return "pedestrian";
        }
        return normalized;
    }

    private static Boolean normalizeBoolean(String value) {
        String cleaned = normalizeText(value);
        if (cleaned == null || isPlaceholder(cleaned)) {
            return null;
        }

        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (Set.of("y", "yes", "true", "1", "t").contains(normalized)) {
            return true;
        }
        if (Set.of("n", "no", "false", "0", "f").contains(normalized)) {
            return false;
        }
        return null;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static boolean isPlaceholder(String value) {
        return PLACEHOLDER_VALUES.contains(value.toLowerCase(Locale.ROOT));
    }

    private static String ArraysToTitleCase(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(text.split(" "))
                .map(part -> part.isEmpty() ? part : part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }
}

