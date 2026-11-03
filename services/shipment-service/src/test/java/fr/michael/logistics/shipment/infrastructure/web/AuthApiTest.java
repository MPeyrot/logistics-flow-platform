package fr.michael.logistics.shipment.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import fr.michael.logistics.shipment.TestAccounts;
import fr.michael.logistics.shipment.TestSecurityConfiguration;
import fr.michael.logistics.shipment.TestcontainersConfiguration;
import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.application.UserAccountRepository;
import fr.michael.logistics.shipment.domain.Role;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    UserAccountRepository repository;

    @Test
    void adminTokenIsSignedByTheKeyPublishedInJwks() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content(credentials(
                        TestSecurityConfiguration.ADMIN_USERNAME, TestSecurityConfiguration.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isString());

        SignedJWT jwt = verifiedWithJwks(
                TestAccounts.token(mvc, TestSecurityConfiguration.ADMIN_USERNAME, TestSecurityConfiguration.ADMIN_PASSWORD));
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(claims.getIssuer()).isEqualTo("shipment-service");
        assertThat(claims.getSubject()).isEqualTo(TestSecurityConfiguration.ADMIN_USERNAME);
        assertThat(claims.getAudience()).containsExactly("shipment-service");
        assertThat(claims.getStringListClaim("roles")).containsExactly("ADMIN");
        assertThat(claims.getClaim("site")).isNull();
        assertThat(lifetime(claims)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void operatorTokenTargetsShipmentServiceForOneHour() throws Exception {
        TestAccounts.ensure(accounts, "auth-operator", Role.OPERATOR, null);

        JWTClaimsSet claims = verifiedWithJwks(TestAccounts.token(mvc, "auth-operator", TestAccounts.PASSWORD))
                .getJWTClaimsSet();

        assertThat(claims.getAudience()).containsExactly("shipment-service");
        assertThat(claims.getStringListClaim("roles")).containsExactly("OPERATOR");
        assertThat(lifetime(claims)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void scannerTokenTargetsIngestionServiceForTwelveHoursWithItsSite() throws Exception {
        TestAccounts.ensure(accounts, "auth-scanner", Role.SCANNER, "LYON");

        JWTClaimsSet claims = verifiedWithJwks(TestAccounts.token(mvc, "auth-scanner", TestAccounts.PASSWORD))
                .getJWTClaimsSet();

        assertThat(claims.getAudience()).containsExactly("ingestion-service");
        assertThat(claims.getStringListClaim("roles")).containsExactly("SCANNER");
        assertThat(claims.getStringClaim("site")).isEqualTo("LYON");
        assertThat(lifetime(claims)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        TestAccounts.ensure(accounts, "Auth-Mixed", Role.OPERATOR, null);

        assertThat(TestAccounts.token(mvc, "auth-mixed", TestAccounts.PASSWORD)).isNotBlank();
    }

    @Test
    void unknownUserWrongPasswordAndDisabledAccountGetTheSame401() throws Exception {
        TestAccounts.ensure(accounts, "auth-disabled", Role.OPERATOR, null);
        repository.save(repository.findByUsername("auth-disabled").orElseThrow().deactivate());

        String unknown = rejected("nobody-here", TestAccounts.PASSWORD);
        String wrongPassword = rejected(TestSecurityConfiguration.ADMIN_USERNAME, "not the right password");
        String disabled = rejected("auth-disabled", TestAccounts.PASSWORD);

        assertThat(wrongPassword).isEqualTo(unknown);
        assertThat(disabled).isEqualTo(unknown);
    }

    @Test
    void jwksExposesOnlyThePublicKey() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isString())
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    @Test
    void neitherPasswordNorTokenIsLogged(CapturedOutput output) throws Exception {
        String token = TestAccounts.token(mvc, TestSecurityConfiguration.ADMIN_USERNAME, TestSecurityConfiguration.ADMIN_PASSWORD);
        rejected(TestSecurityConfiguration.ADMIN_USERNAME, "secret-attempt-password");
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/shipments").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());

        assertThat(output.getAll())
                .doesNotContain(token)
                .doesNotContain(TestSecurityConfiguration.ADMIN_PASSWORD)
                .doesNotContain("secret-attempt-password");
    }

    private String rejected(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON).content(credentials(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn().getResponse().getContentAsString();
    }

    private SignedJWT verifiedWithJwks(String token) throws Exception {
        String jwks = mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        SignedJWT jwt = SignedJWT.parse(token);
        RSAKey key = JWKSet.parse(jwks).getKeyByKeyId(jwt.getHeader().getKeyID()).toRSAKey();
        assertThat(jwt.verify(new RSASSAVerifier(key))).as("signature vérifiée avec la clé du JWKS").isTrue();
        return jwt;
    }

    private static Duration lifetime(JWTClaimsSet claims) {
        return Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant());
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }
}
