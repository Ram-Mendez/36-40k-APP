package com.acme.operations.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Authentication")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/auth")
public class AuthController {
    // The controller delegates business operations to this service.
    private final AuthService service;

    // Spring injects the dependencies through the single constructor.
    public AuthController(AuthService service) {
        // Keep the injected dependency for later calls.
        this.service = service;
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Sign in")
    @PostMapping("/login")
    // It delivers credentials to the service and returns the tokens if they are valid. Spring converts and
    // validates the JSON before entering.
    public AuthDtos.Tokens login(@Valid @RequestBody AuthDtos.Login request) {
        // Deliver credentials to the service and return the tokens if valid.
        return service.login(request.username(), request.password());
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Refresh a session")
    @PostMapping("/refresh")
    // Refresh the tokens. The service checks the session and rejects token reuse.
    public AuthDtos.Tokens refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        // Refresh the tokens. The service checks the session and rejects token reuse.
        return service.refresh(request.refreshToken());
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Sign out")
    @PostMapping("/logout")
    // Return HTTP 204 with an empty body on success.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Close the family identified by Sid, obtained from the authenticated JWT.
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        // Close the family identified by Sid, obtained from the authenticated JWT.
        service.logout(jwt.getClaimAsString("sid"));
    }

    // Change the password of the authenticated identity and leave your previous sessions invalid.
    @Operation(summary = "Change the current password")
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthDtos.ChangePassword request) {
        service.changePassword(jwt.getSubject(), request);
    }

    // Update the authenticated user profile.
    @Operation(summary = "Update the signed-in user profile")
    @PutMapping("/user")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthDtos.UpdateUser request) {

        // It uses the JWT identity, not the body identity.
        service.updateUser(jwt.getSubject(), request);
    }
}
