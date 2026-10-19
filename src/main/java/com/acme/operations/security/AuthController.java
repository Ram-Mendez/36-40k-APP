package com.acme.operations.security;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public AuthDtos.Tokens login(@Valid @RequestBody AuthDtos.Login request) {
        return service.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public AuthDtos.Tokens refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        service.logout(jwt.getClaimAsString("sid"));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthDtos.PasswordChange request) {
        service.changePassword(jwt.getSubject(), request);
    }
}
