package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AccessToken;
import fr.michael.logistics.shipment.application.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationService authentication;

    AuthController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/token")
    TokenResponse token(@Valid @RequestBody TokenRequest request) {
        AccessToken token = authentication.issueToken(request.username(), request.password());
        return new TokenResponse(token.value(), "Bearer", token.expiresAt());
    }

    record TokenRequest(@NotBlank String username, @NotBlank String password) {

        /** Jamais le mot de passe dans les journaux. */
        @Override
        public String toString() {
            return "TokenRequest[username=" + username + "]";
        }
    }

    record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {

        @Override
        public String toString() {
            return "TokenResponse[expiresAt=" + expiresAt + "]";
        }
    }
}
