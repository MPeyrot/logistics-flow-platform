package fr.michael.logistics.ingestion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.michael.logistics.ingestion.TestcontainersConfiguration;
import fr.michael.logistics.ingestion.infrastructure.security.TestJwt;
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

/** Vrai serveur : le plafond de taille est appliqué à l'analyse multipart, avant le contrôleur. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=1KB"})
@Import(TestcontainersConfiguration.class)
class ScanImportLimitTest {

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @LocalServerPort
    int port;

    @Test
    void aFileOverTheSizeCapIsPayloadTooLargeProblem() throws Exception {
        String boundary = "----limit";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"scans.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n" + "x".repeat(4000) + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/scans/imports"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + TestJwt.scanner("Nantes").build())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(response.body()).contains("The uploaded file is too large");
    }
}
