package fr.michael.logistics.shipment.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentApiTest {

    @Autowired
    MockMvc mvc;

    String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = TestAccounts.adminBearer(mvc);
    }

    @Test
    void createsThenListsShipment() throws Exception {
        mvc.perform(post("/api/shipments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference":"API-1","origin":"Nantes","destination":"Lyon"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        mvc.perform(get("/api/shipments").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reference=='API-1')]").exists());
    }

    @Test
    void rejectsDuplicateReference() throws Exception {
        String body = """
                {"reference":"API-DUP","origin":"Nantes","destination":"Lyon"}""";
        mvc.perform(post("/api/shipments").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/shipments").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsBlankFields() throws Exception {
        mvc.perform(post("/api/shipments").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference":"","origin":"Nantes","destination":"Lyon"}"""))
                .andExpect(status().isBadRequest());
    }
}
