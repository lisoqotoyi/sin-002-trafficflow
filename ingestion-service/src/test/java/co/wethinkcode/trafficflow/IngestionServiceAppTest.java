package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class IngestionServiceAppTest {

    @Test
    void shouldNormalizeAndDeduplicateIntersections() {
        List<Map<String, Object>> records = IngestionServiceApp.readCleanedRecords();

        assertFalse(records.isEmpty());
        Map<String, Object> record = findRecord(records, "INT-1005");
        assertNotNull(record);
        assertEquals("Downtown", record.get("district"));
        assertEquals("roundabout", record.get("signalType"));
        assertEquals(Boolean.TRUE, record.get("active"));
    }

    @Test
    void shouldPreserveExplicitMissingValues() {
        List<Map<String, Object>> records = IngestionServiceApp.readCleanedRecords();

        Map<String, Object> record = findRecord(records, "INT-1015");
        assertNull(record.get("district"));
        assertEquals("4-way", record.get("signalType"));
        assertEquals(Boolean.TRUE, record.get("active"));
    }

    private Map<String, Object> findRecord(List<Map<String, Object>> records, String id) {
        return records.stream()
                .filter(record -> id.equalsIgnoreCase((String) record.get("id")))
                .findFirst()
                .orElseThrow();
    }
}
