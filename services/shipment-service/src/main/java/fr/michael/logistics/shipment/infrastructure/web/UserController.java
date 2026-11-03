package fr.michael.logistics.shipment.infrastructure.web;

import fr.michael.logistics.shipment.application.AccountService;
import fr.michael.logistics.shipment.domain.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Comptes : création et désactivation, réservées à l'ADMIN (SecurityConfig). Pas de liste. */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final AccountService accounts;

    UserController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserAccount account = accounts.create(request.username(), request.password(), request.role(), request.site());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(account.id()).toUri();
        return ResponseEntity.created(location).body(UserResponse.from(account));
    }

    /** Corps {"active": false} : seule la désactivation existe (spec #13). */
    @PatchMapping("/{id}")
    UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(accounts.deactivate(id));
    }

    record UpdateUserRequest(@NotNull @AssertFalse(message = "only deactivation is supported") Boolean active) {
    }
}
