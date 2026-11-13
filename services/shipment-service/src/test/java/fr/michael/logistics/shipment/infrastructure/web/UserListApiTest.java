package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.domain.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserListApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Test
    void adminListsAccountsSortedByUsernameWithoutPasswords() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String b = "ulist-b-" + suffix;
        String a = "ulist-a-" + suffix;
        accounts.create(b, "mot-de-passe-long-1", Role.SCANNER, "NANTES");
        accounts.create(a, "mot-de-passe-long-2", Role.OPERATOR, null);

        String body = mvc.perform(get("/api/users").header("Authorization", TestAccounts.adminBearer(mvc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username=='%s')].role".formatted(b)).value(contains("SCANNER")))
                .andExpect(jsonPath("$[?(@.username=='%s')].site".formatted(b)).value(contains("NANTES")))
                .andExpect(jsonPath("$[?(@.username=='%s')].active".formatted(a)).value(contains(true)))
                .andReturn().getResponse().getContentAsString();

        List<String> usernames = JsonPath.read(body, "$[*].username");
        assertThat(usernames.indexOf(a)).isGreaterThanOrEqualTo(0).isLessThan(usernames.indexOf(b));
        assertThat(body).doesNotContain("assword").doesNotContain("$2");
    }

    @Test
    void operatorAndScannerGet403AndAnonymousGets401() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", TestAccounts.operatorBearer(mvc, accounts)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", TestAccounts.scannerBearer(mvc, accounts)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
}
