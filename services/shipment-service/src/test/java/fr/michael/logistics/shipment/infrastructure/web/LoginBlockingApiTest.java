package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 5 échecs de connexion en 15 min bloquent l'identifiant 15 min (valeurs par défaut). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginBlockingApiTest {

    private static final String WRONG_PASSWORD = "not the right password";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Test
    void fiveFailuresBlockTheUsernameEvenWithTheRightPassword() throws Exception {
        TestAccounts.ensure(accounts, "blocked-operator", Role.OPERATOR, null);
        for (int i = 0; i < 5; i++) {
            login("blocked-operator", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        MockHttpServletResponse blocked = login("blocked-operator", TestAccounts.PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse();

        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isBetween(890L, 900L);
        login("BLOCKED-OPERATOR", TestAccounts.PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void fourFailuresThenTheRightPasswordLogsInAndResetsTheCounter() throws Exception {
        TestAccounts.ensure(accounts, "careless-operator", Role.OPERATOR, null);
        for (int i = 0; i < 4; i++) {
            login("careless-operator", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login("careless-operator", TestAccounts.PASSWORD).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login("careless-operator", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login("careless-operator", TestAccounts.PASSWORD).andExpect(status().isOk());
    }

    /** Un identifiant inconnu est bloqué de la même façon : la réponse ne révèle pas quels comptes existent. */
    @Test
    void unknownUsernameIsBlockedToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("blocked-nobody", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login("blocked-nobody", WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void blockingOneUsernameLeavesOthersAlone() throws Exception {
        TestAccounts.ensure(accounts, "blocked-neighbour", Role.OPERATOR, null);
        for (int i = 0; i < 5; i++) {
            login("blocked-attacked", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        login("blocked-neighbour", TestAccounts.PASSWORD).andExpect(status().isOk());
    }

    /**
     * Un identifiant plus long qu'à la création de compte est refusé avant {@code LoginThrottle} : 400, jamais
     * compté comme un échec, donc jamais bloqué (429) ni gardé en mémoire.
     */
    @Test
    void tooLongUsernameIsRejectedWithoutCountingAFailure() throws Exception {
        String tooLong = "x".repeat(UserAccount.USERNAME_MAX_LENGTH + 1);
        for (int i = 0; i < 6; i++) {
            login(tooLong, WRONG_PASSWORD).andExpect(status().isBadRequest());
        }

        login("y".repeat(UserAccount.USERNAME_MAX_LENGTH), WRONG_PASSWORD).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }
}
