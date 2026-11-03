package fr.michael.logistics.ingestion.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Vrai serveur : MockMvc ne fait pas le dispatch ERROR vers {@code /error}, où une erreur 400 devenait 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ErrorDispatchTest {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        HttpResponse<String> response = scan("{not json", TestJwt.scanner("Nantes").build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void blankShipmentReferenceIsBadRequest() throws Exception {
        HttpResponse<String> response = scan("""
                {"shipmentReference":" ","type":"PICKED_UP","site":"Nantes"}""", TestJwt.scanner("Nantes").build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void scanForAnotherSiteKeepsItsDetail() throws Exception {
        HttpResponse<String> response = scan("""
                {"shipmentReference":"ERR-1","type":"PICKED_UP","site":"Lyon"}""", TestJwt.scanner("Nantes").build());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("Scan site does not match the token site");
    }

    private HttpResponse<String> scan(String body, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/scans"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
