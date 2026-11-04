package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AccessToken;
import fr.michael.logistics.shipment.application.AuthenticationService;
import fr.michael.logistics.shipment.domain.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /** Même longueur maximale qu'à la création de compte : rien de plus long n'atteint {@code LoginThrottle}. */
    record TokenRequest(@NotBlank @Size(max = UserAccount.USERNAME_MAX_LENGTH) String username,
            @NotBlank String password) {

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
