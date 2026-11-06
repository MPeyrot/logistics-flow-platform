package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AnomalyApiTest {

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
    void authenticate() throws Exception {
        admin = TestAccounts.adminBearer(mvc);
        reference = "ANO-" + UUID.randomUUID();
        shipmentId = shipment(reference);
    }

    @Test
    void listsAnomaliesNewestFirstWithTheirShipmentReference() throws Exception {
        UUID older = anomaly(shipmentId, "STALLED", "2030-01-01T10:00:00Z", null);
        UUID newer = anomaly(shipmentId, "LATE", "2030-01-02T10:00:00Z", "2030-01-03T10:00:00Z");

        mvc.perform(get("/api/anomalies").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.shipmentReference=='%s')].id".formatted(reference))
                        .value(contains(newer.toString(), older.toString())))
                .andExpect(jsonPath("$[?(@.id=='%s')].type".formatted(newer)).value(contains("LATE")))
                .andExpect(jsonPath("$[?(@.id=='%s')].detectedAt".formatted(newer))
                        .value(contains("2030-01-02T10:00:00Z")))
                .andExpect(jsonPath("$[?(@.id=='%s')].resolvedAt".formatted(newer))
                        .value(contains("2030-01-03T10:00:00Z")))
                .andExpect(jsonPath("$[?(@.id=='%s')].resolvedAt".formatted(older)).value(contains(nullValue())));
    }

    @Test
    void openFilterKeepsOpenOrClosedAnomalies() throws Exception {
        UUID open = anomaly(shipmentId, "STALLED", "2030-01-01T10:00:00Z", null);
        UUID closed = anomaly(shipmentId, "LATE", "2030-01-02T10:00:00Z", "2030-01-03T10:00:00Z");

        mvc.perform(get("/api/anomalies?open=true").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.shipmentReference=='%s')].id".formatted(reference))
                        .value(contains(open.toString())));
        mvc.perform(get("/api/anomalies?open=false").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.shipmentReference=='%s')].id".formatted(reference))
                        .value(contains(closed.toString())));
    }

    @Test
    void invalidOpenParameterGets400() throws Exception {
        mvc.perform(get("/api/anomalies?open=peut-etre").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operatorMayReadButScannerAndAnonymousMayNot() throws Exception {
        mvc.perform(get("/api/anomalies").header("Authorization", TestAccounts.operatorBearer(mvc, accounts)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/anomalies").header("Authorization", TestAccounts.scannerBearer(mvc, accounts)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies")).andExpect(status().isUnauthorized());
    }

    @Test
    void shipmentListCarriesOnlyItsOpenAnomalies() throws Exception {
        anomaly(shipmentId, "STALLED", "2030-01-01T10:00:00Z", null);
        anomaly(shipmentId, "LATE", "2030-01-02T10:00:00Z", "2030-01-03T10:00:00Z");
        String other = "ANO-NONE-" + UUID.randomUUID();
        shipment(other);

        mvc.perform(get("/api/shipments").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reference=='%s')].openAnomalies[0].type".formatted(reference))
                        .value(contains("STALLED")))
                .andExpect(jsonPath("$[?(@.reference=='%s')].openAnomalies[0].detectedAt".formatted(reference))
                        .value(contains("2030-01-01T10:00:00Z")))
                .andExpect(jsonPath("$[?(@.reference=='%s')].openAnomalies[1]".formatted(reference)).isEmpty())
                .andExpect(jsonPath("$[?(@.reference=='%s')].openAnomalies".formatted(other))
                        .value(contains(empty())));
    }

    @Test
    void listingShipmentsDoesNotQueryOncePerShipment() throws Exception {
        for (int i = 0; i < 5; i++) {
            UUID id = shipment("ANO-N1-" + UUID.randomUUID());
            anomaly(id, "STALLED", "2030-01-01T10:00:00Z", null);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        mvc.perform(get("/api/shipments").header("Authorization", admin)).andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        statistics.setStatisticsEnabled(false);
    }

    private UUID shipment(String ref) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shipment (id, reference, origin, destination, status, created_at) "
                + "VALUES (?, ?, 'Nantes', 'Lyon', 'IN_TRANSIT', now())", id, ref);
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
