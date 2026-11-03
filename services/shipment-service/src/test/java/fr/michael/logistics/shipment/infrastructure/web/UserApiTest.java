package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestSecurityConfiguration;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.application.DuplicateUsernameException;
import fr.michael.logistics.shipment.application.UserAccountRepository;
import fr.michael.logistics.shipment.domain.Role;
import fr.michael.logistics.shipment.domain.UserAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** API des comptes (POST et PATCH /api/users), avec de vrais jetons. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserApiTest {

    private static final String PASSWORD = "a long enough password";
    private static final String DEACTIVATE = "{\"active\":false}";
    private static final String REACTIVATE = "{\"active\":true}";

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    UserAccountRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void adminCreatesAnAccountThatCanThenLogIn() throws Exception {
        createUser("users-operator", PASSWORD, "OPERATOR", null)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/users/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.username").value("users-operator"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.site").doesNotExist())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(TestAccounts.token(mvc, "users-operator", PASSWORD)).isNotBlank();
    }

    @Test
    void scannerIsCreatedWithItsSite() throws Exception {
        createUser("users-scanner", PASSWORD, "SCANNER", "NANTES")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SCANNER"))
                .andExpect(jsonPath("$.site").value("NANTES"));
    }

    @Test
    void rejectsUsernameOutsideThreeToFiftyCharacters() throws Exception {
        badRequest(createUser("ab", PASSWORD, "OPERATOR", null));
        badRequest(createUser("a".repeat(51), PASSWORD, "OPERATOR", null));
        badRequest(createUser(null, PASSWORD, "OPERATOR", null));
        createUser("abc", PASSWORD, "OPERATOR", null).andExpect(status().isCreated());
        createUser("b".repeat(50), PASSWORD, "OPERATOR", null).andExpect(status().isCreated());
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() throws Exception {
        badRequest(createUser("users-short-pw", "elevenchars", "OPERATOR", null));
        badRequest(createUser("users-no-pw", null, "OPERATOR", null));
        createUser("users-twelve-pw", "twelve chars", "OPERATOR", null).andExpect(status().isCreated());
    }

    /** BCrypt ne lit que 72 octets : au-delà, deux mots de passe différents auraient la même empreinte. */
    @Test
    void rejectsPasswordLongerThanSeventyTwoBytesInUtf8() throws Exception {
        badRequest(createUser("users-long-pw", "p".repeat(73), "OPERATOR", null));
        // 36 caractères mais 72 octets : accepté ; 37 caractères, 74 octets : refusé.
        createUser("users-utf8-pw-ok", "é".repeat(36), "OPERATOR", null).andExpect(status().isCreated());
        badRequest(createUser("users-utf8-pw-ko", "é".repeat(37), "OPERATOR", null));
        assertThat(TestAccounts.token(mvc, "users-utf8-pw-ok", "é".repeat(36))).isNotBlank();
    }

    @Test
    void scannerNeedsASiteAndOnlyScannerHasOne() throws Exception {
        badRequest(createUser("users-scanner-nosite", PASSWORD, "SCANNER", null));
        badRequest(createUser("users-scanner-blank", PASSWORD, "SCANNER", " "));
        badRequest(createUser("users-operator-site", PASSWORD, "OPERATOR", "NANTES"));
        badRequest(createUser("users-admin-site", PASSWORD, "ADMIN", "NANTES"));
    }

    @Test
    void rejectsMissingOrUnknownRole() throws Exception {
        badRequest(createUser("users-no-role", PASSWORD, null, null));
        badRequest(createUser("users-bad-role", PASSWORD, "ROOT", null));
    }

    @Test
    void usernameAlreadyTakenWhateverTheCaseGets409() throws Exception {
        createUser("Users-Taken", PASSWORD, "OPERATOR", null).andExpect(status().isCreated());

        createUser("users-taken", PASSWORD, "ADMIN", null)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        createUser("USERS-TAKEN", PASSWORD, "OPERATOR", null).andExpect(status().isConflict());
    }

    /** Deux créations simultanées passent toutes deux la vérification : l'index unique tranche, en 409. */
    @Test
    void uniqueIndexReportsADuplicateMissedByTheCheck() {
        repository.save(UserAccount.create("users-race", "hash", Role.OPERATOR, null, Instant.now()));

        assertThatThrownBy(() -> repository.save(UserAccount.create("USERS-RACE", "hash", Role.OPERATOR, null, Instant.now())))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void deactivatedAccountGetsTheSame401AsAnUnknownUser() throws Exception {
        String id = idOf(createUser("users-to-disable", PASSWORD, "OPERATOR", null));
        assertThat(TestAccounts.token(mvc, "users-to-disable", PASSWORD)).isNotBlank();

        deactivate(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.active").value(false));

        assertThat(rejectedLogin("users-to-disable", PASSWORD)).isEqualTo(rejectedLogin("users-never-existed", PASSWORD));
    }

    @Test
    void deactivatingTwiceIsHarmless() throws Exception {
        String id = idOf(createUser("users-disable-twice", PASSWORD, "SCANNER", "LYON"));

        deactivate(id).andExpect(status().isOk());
        deactivate(id).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivatedAccountCanBeReactivatedAndLogInAgain() throws Exception {
        String id = idOf(createUser("users-to-reactivate", PASSWORD, "SCANNER", "LYON"));
        deactivate(id).andExpect(status().isOk());
        rejectedLogin("users-to-reactivate", PASSWORD);

        reactivate(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.username").value("users-to-reactivate"))
                .andExpect(jsonPath("$.role").value("SCANNER"))
                .andExpect(jsonPath("$.site").value("LYON"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(TestAccounts.token(mvc, "users-to-reactivate", PASSWORD)).isNotBlank();
    }

    @Test
    void reactivatingAnActiveAccountIsHarmless() throws Exception {
        String id = idOf(createUser("users-reactivate-twice", PASSWORD, "OPERATOR", null));

        reactivate(id).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        reactivate(id).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertThat(TestAccounts.token(mvc, "users-reactivate-twice", PASSWORD)).isNotBlank();
    }

    @Test
    void patchNeedsTheActiveFlag() throws Exception {
        String id = idOf(createUser("users-patch-body", PASSWORD, "OPERATOR", null));

        badRequest(patch(id, "{}"));
        badRequest(patch(id, "{\"active\":null}"));
        badRequest(patch(id, "{\"active\":\"maybe\"}"));
        assertThat(TestAccounts.token(mvc, "users-patch-body", PASSWORD)).isNotBlank();
    }

    @Test
    void unknownAccountGets404() throws Exception {
        deactivate(UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        reactivate(UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        badRequest(deactivate("not-a-uuid"));
        badRequest(reactivate("not-a-uuid"));
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() throws Exception {
        String otherAdmin = idOf(createUser("users-second-admin", PASSWORD, "ADMIN", null));
        // Mise en place : seuls test-admin et users-second-admin restent des ADMIN actifs.
        jdbc.update("update user_account set active = false where role = 'ADMIN' and lower(username) not in (?, ?)",
                TestSecurityConfiguration.ADMIN_USERNAME, "users-second-admin");

        deactivate(otherAdmin).andExpect(status().isOk());

        String testAdmin = repository.findByUsername(TestSecurityConfiguration.ADMIN_USERNAME).orElseThrow().id().toString();
        deactivate(testAdmin)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(TestAccounts.adminBearer(mvc)).isNotBlank();
    }

    @Test
    void operatorAndScannerGet403OnUsers() throws Exception {
        String id = idOf(createUser("users-target", PASSWORD, "OPERATOR", null));
        String disabled = idOf(createUser("users-target-disabled", PASSWORD, "OPERATOR", null));
        deactivate(disabled).andExpect(status().isOk());
        for (String bearer : new String[] {
                TestAccounts.operatorBearer(mvc, accounts), TestAccounts.scannerBearer(mvc, accounts)}) {
            mvc.perform(post("/api/users").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(json("users-by-other", PASSWORD, "ADMIN", null)))
                    .andExpect(status().isForbidden());
            mvc.perform(MockMvcRequestBuilders.patch("/api/users/" + id).header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(DEACTIVATE))
                    .andExpect(status().isForbidden());
            mvc.perform(MockMvcRequestBuilders.patch("/api/users/" + disabled).header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(REACTIVATE))
                    .andExpect(status().isForbidden());
        }
        assertThat(TestAccounts.token(mvc, "users-target", PASSWORD)).isNotBlank();
        assertThat(repository.findById(UUID.fromString(disabled)).orElseThrow().active()).isFalse();
        assertThat(repository.findByUsername("users-by-other")).isEmpty();
    }

    @Test
    void requestWithoutTokenGets401OnUsers() throws Exception {
        String id = idOf(createUser("users-target-anon", PASSWORD, "OPERATOR", null));
        String disabled = idOf(createUser("users-target-anon-disabled", PASSWORD, "OPERATOR", null));
        deactivate(disabled).andExpect(status().isOk());

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(json("users-by-anon", PASSWORD, "ADMIN", null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.patch("/api/users/" + id).contentType(MediaType.APPLICATION_JSON).content(DEACTIVATE))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.patch("/api/users/" + disabled).contentType(MediaType.APPLICATION_JSON).content(REACTIVATE))
                .andExpect(status().isUnauthorized());
        assertThat(repository.findByUsername("users-by-anon")).isEmpty();
        assertThat(repository.findById(UUID.fromString(disabled)).orElseThrow().active()).isFalse();
    }

    private ResultActions deactivate(String id) throws Exception {
        return patch(id, DEACTIVATE);
    }

    private ResultActions reactivate(String id) throws Exception {
        return patch(id, REACTIVATE);
    }

    private ResultActions patch(String id, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/users/" + id)
                .header("Authorization", TestAccounts.adminBearer(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String rejectedLogin(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }

    private static String idOf(ResultActions created) throws Exception {
        String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static void badRequest(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest());
    }

    private ResultActions createUser(String username, String password, String role, String site) throws Exception {
        return mvc.perform(post("/api/users")
                .header("Authorization", TestAccounts.adminBearer(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(username, password, role, site)));
    }

    private static String json(String username, String password, String role, String site) {
        return "{\"username\":%s,\"password\":%s,\"role\":%s,\"site\":%s}"
                .formatted(quoted(username), quoted(password), quoted(role), quoted(site));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
