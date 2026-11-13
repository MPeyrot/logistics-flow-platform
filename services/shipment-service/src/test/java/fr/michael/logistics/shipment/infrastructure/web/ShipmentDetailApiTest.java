package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentDetailApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountService accounts;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    String admin;
    String reference;
    UUID shipmentId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAccounts.adminBearer(mvc);
        reference = "DET-" + UUID.randomUUID();
        shipmentId = shipment(reference);
    }

    @Test
    void returnsShipmentFieldsWithOpenAnomalies() throws Exception {
        anomaly(shipmentId, "STALLED", "2030-01-01T10:00:00Z", null);

        mvc.perform(get("/api/shipments/" + reference).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.origin").value("Nantes"))
                .andExpect(jsonPath("$.destination").value("Lyon"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.openAnomalies[0].type").value("STALLED"))
                .andExpect(jsonPath("$.openAnomalies[0].detectedAt").value("2030-01-01T10:00:00Z"));
    }

    @Test
    void scansComeInChronologicalOrderAndReasonIsOmittedWhenAbsent() throws Exception {
        UUID late = scan("ARRIVED_AT_SITE", "LYON", "2030-01-02T10:00:00Z", "2030-01-02T10:00:05Z", null);
        UUID first = scan("PICKED_UP", "NANTES", "2030-01-01T10:00:00Z", "2030-01-03T10:00:00Z", null);
        UUID tieLater = scan("DELIVERY_FAILED", "LYON", "2030-01-02T10:00:00Z", "2030-01-02T10:00:09Z", "Absent");

        mvc.perform(get("/api/shipments/" + reference).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scans[*].eventId")
                        .value(contains(first.toString(), late.toString(), tieLater.toString())))
                .andExpect(jsonPath("$.scans[0].type").value("PICKED_UP"))
                .andExpect(jsonPath("$.scans[0].site").value("NANTES"))
                .andExpect(jsonPath("$.scans[0].occurredAt").value("2030-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.scans[0].receivedAt").value("2030-01-03T10:00:00Z"))
                .andExpect(jsonPath("$.scans[0]", not(hasKey("reason"))))
                .andExpect(jsonPath("$.scans[2].reason").value("Absent"));
    }

    @Test
    void anomaliesIncludeOpenAndClosedOnes() throws Exception {
        UUID open = anomaly(shipmentId, "STALLED", "2030-01-01T10:00:00Z", null);
        UUID closed = anomaly(shipmentId, "LATE", "2030-01-02T10:00:00Z", "2030-01-03T10:00:00Z");

        mvc.perform(get("/api/shipments/" + reference).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anomalies.length()").value(2))
                .andExpect(jsonPath("$.anomalies[?(@.id=='%s')].type".formatted(open)).value(contains("STALLED")))
                .andExpect(jsonPath("$.anomalies[?(@.id=='%s')].resolvedAt".formatted(closed))
                        .value(contains("2030-01-03T10:00:00Z")))
                .andExpect(jsonPath("$.openAnomalies.length()").value(1));
    }

    @Test
    void shipmentWithoutScansNorAnomaliesHasEmptyLists() throws Exception {
        mvc.perform(get("/api/shipments/" + reference).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scans.length()").value(0))
                .andExpect(jsonPath("$.anomalies.length()").value(0));
    }

    @Test
    void unknownReferenceGets404ProblemJson() throws Exception {
        mvc.perform(get("/api/shipments/INCONNUE-" + UUID.randomUUID()).header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void operatorMayReadButScannerAndAnonymousMayNot() throws Exception {
        mvc.perform(get("/api/shipments/" + reference)
                        .header("Authorization", TestAccounts.operatorBearer(mvc, accounts)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/shipments/" + reference)
                        .header("Authorization", TestAccounts.scannerBearer(mvc, accounts)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/shipments/" + reference)).andExpect(status().isUnauthorized());
    }

    @Test
    void detailDoesNotQueryOncePerScanOrAnomaly() throws Exception {
        for (int i = 0; i < 5; i++) {
            scan("ARRIVED_AT_SITE", "LYON", "2030-01-01T1" + i + ":00:00Z", "2030-01-01T1" + i + ":00:01Z", null);
            anomaly(shipmentId, i % 2 == 0 ? "STALLED" : "LATE", "2030-01-01T1" + i + ":00:00Z",
                    "2030-01-02T10:00:00Z");
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mvc.perform(get("/api/shipments/" + reference).header("Authorization", admin)).andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        statistics.setStatisticsEnabled(false);
    }

    private UUID shipment(String ref) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shipment (id, reference, origin, destination, status, created_at) "
                + "VALUES (?, ?, 'Nantes', 'Lyon', 'IN_TRANSIT', now())", id, ref);
        return id;
    }

    private UUID scan(String type, String site, String occurredAt, String receivedAt, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO scan_event (event_id, shipment_id, type, site, occurred_at, reason, received_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, shipmentId, type, site, Timestamp.from(Instant.parse(occurredAt)), reason,
                Timestamp.from(Instant.parse(receivedAt)));
        return id;
    }

    private UUID anomaly(UUID shipment, String type, String detectedAt, String resolvedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO anomaly (id, shipment_id, type, detected_at, resolved_at) VALUES (?, ?, ?, ?, ?)",
                id, shipment, type, Timestamp.from(Instant.parse(detectedAt)),
                resolvedAt == null ? null : Timestamp.from(Instant.parse(resolvedAt)));
        return id;
    }
}
