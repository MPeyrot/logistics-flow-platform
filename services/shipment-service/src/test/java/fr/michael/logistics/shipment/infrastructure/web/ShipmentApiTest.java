package fr.michael.logistics.shipment.infrastructure.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentApiTest {

    /** Une date de livraison prévue valide : dans trois jours, à la seconde. */
    static final Instant DUE = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = TestAccounts.adminBearer(mvc);
    }

    @Test
    void createsThenListsShipment() throws Exception {
        create("""
                {"reference":"API-1","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"%s"}""".formatted(DUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.expectedDeliveryAt").value(DUE.toString()));

        mvc.perform(get("/api/shipments").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reference=='API-1')].expectedDeliveryAt").value(contains(DUE.toString())));
    }

    @Test
    void expectedDeliveryDateWithAnOffsetIsReturnedInUtc() throws Exception {
        String withOffset = DUE.atOffset(ZoneOffset.ofHours(2)).toString();

        create("""
                {"reference":"API-OFFSET","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"%s"}"""
                .formatted(withOffset))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedDeliveryAt").value(DUE.toString()));
    }

    @Test
    void rejectsDuplicateReference() throws Exception {
        String body = """
                {"reference":"API-DUP","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"%s"}""".formatted(DUE);
        create(body).andExpect(status().isCreated());
        create(body).andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankFields() throws Exception {
        create("""
                {"reference":"","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"%s"}""".formatted(DUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingExpectedDeliveryDateGets400Problem() throws Exception {
        badRequestProblem(create("""
                {"reference":"API-NO-DUE","origin":"Nantes","destination":"Lyon"}"""))
                .andExpect(jsonPath("$.detail").value("expectedDeliveryAt: must not be null"));
        badRequestProblem(create("""
                {"reference":"API-NULL-DUE","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":null}"""));
    }

    @Test
    void malformedExpectedDeliveryDateGets400Problem() throws Exception {
        badRequestProblem(create("""
                {"reference":"API-BAD-DUE","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"demain"}"""))
                .andExpect(jsonPath("$.detail").value("Invalid value for expectedDeliveryAt"));
        badRequestProblem(create("""
                {"reference":"API-BAD-DUE","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"2026-13-01T10:00:00Z"}"""));
    }

    @Test
    void numericExpectedDeliveryDateGets400Problem() throws Exception {
        badRequestProblem(create("""
                {"reference":"API-NUM-DUE","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":4102444800}"""))
                .andExpect(jsonPath("$.detail").value("Invalid value for expectedDeliveryAt"));
        badRequestProblem(create("""
                {"reference":"API-NUM-DUE","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":4102444800.5}"""));
    }

    @Test
    void expectedDeliveryDateInThePastGets400Problem() throws Exception {
        String past = Instant.now().minus(Duration.ofMinutes(1)).toString();
        badRequestProblem(create("""
                {"reference":"API-PAST-DUE","origin":"Nantes","destination":"Lyon","expectedDeliveryAt":"%s"}"""
                .formatted(past)))
                .andExpect(jsonPath("$.detail").value("expectedDeliveryAt must be in the future"));

        mvc.perform(get("/api/shipments").header("Authorization", bearer))
                .andExpect(jsonPath("$[?(@.reference=='API-PAST-DUE')]").isEmpty());
    }

    @Test
    void shipmentCreatedBeforeTheExpectedDeliveryDateHasNone() throws Exception {
        String reference = "API-LEGACY-" + UUID.randomUUID();
        jdbc.update("INSERT INTO shipment (id, reference, origin, destination, status, created_at) "
                + "VALUES (?, ?, 'Nantes', 'Lyon', 'CREATED', now())", UUID.randomUUID(), reference);

        mvc.perform(get("/api/shipments").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reference=='%s')].expectedDeliveryAt".formatted(reference))
                        .value(contains(nullValue())));
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/shipments").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static ResultActions badRequestProblem(ResultActions result) throws Exception {
        return result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}
