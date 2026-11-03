package fr.michael.logistics.shipment.infrastructure.security;

import fr.michael.logistics.shipment.application.PasswordHasher;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BcryptPasswordHasher implements PasswordHasher {

    /** BCrypt ne lit que 72 octets et Spring Security refuse un mot de passe plus long. */
    private static final int MAX_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        // Plus long que MAX_BYTES : ne peut pas être le bon mot de passe (et encoder.matches lèverait une exception).
        if (rawPassword == null || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }
}
