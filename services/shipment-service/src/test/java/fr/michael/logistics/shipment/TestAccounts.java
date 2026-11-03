package fr.michael.logistics.shipment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.application.DuplicateUsernameException;
import fr.michael.logistics.shipment.domain.Role;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Comptes de test et jetons obtenus par la vraie route POST /api/auth/token. */
public final class TestAccounts {

    public static final String PASSWORD = "a test password";

    private TestAccounts() {
    }

    /** Crée le compte s'il n'existe pas encore (le contexte et la base sont partagés entre classes de test). */
    public static void ensure(AccountService accounts, String username, Role role, String site) {
        try {
            accounts.create(username, PASSWORD, role, site);
        } catch (DuplicateUsernameException alreadyCreated) {
            // créé par une autre classe de test
        }
    }

    public static String adminBearer(MockMvc mvc) throws Exception {
        return bearer(mvc, TestSecurityConfiguration.ADMIN_USERNAME, TestSecurityConfiguration.ADMIN_PASSWORD);
    }

    public static String operatorBearer(MockMvc mvc, AccountService accounts) throws Exception {
        ensure(accounts, "test-operator", Role.OPERATOR, null);
        return bearer(mvc, "test-operator", PASSWORD);
    }

    public static String scannerBearer(MockMvc mvc, AccountService accounts) throws Exception {
        ensure(accounts, "test-scanner", Role.SCANNER, "NANTES");
        return bearer(mvc, "test-scanner", PASSWORD);
    }

    public static String bearer(MockMvc mvc, String username, String password) throws Exception {
        return "Bearer " + token(mvc, username, password);
    }

    public static String token(MockMvc mvc, String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
