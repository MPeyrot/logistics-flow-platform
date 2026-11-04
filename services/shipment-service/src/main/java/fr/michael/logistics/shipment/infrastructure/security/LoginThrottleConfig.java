package fr.michael.logistics.shipment.infrastructure.security;

import fr.michael.logistics.shipment.application.LoginThrottle;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Seuils du blocage après échecs de connexion (application.properties, auth.login.*). */
@Configuration
class LoginThrottleConfig {

    @Bean
    LoginThrottle loginThrottle(
            @Value("${auth.login.max-failures}") int maxFailures,
            @Value("${auth.login.failure-window}") Duration failureWindow,
            @Value("${auth.login.block-duration}") Duration blockDuration,
            @Value("${auth.login.max-tracked-usernames}") int maxTrackedUsernames,
            @Value("${auth.login.max-blocked-usernames}") int maxBlockedUsernames,
            Clock clock) {
        return new LoginThrottle(maxFailures, failureWindow, blockDuration, maxTrackedUsernames, maxBlockedUsernames,
                clock);
    }
}
