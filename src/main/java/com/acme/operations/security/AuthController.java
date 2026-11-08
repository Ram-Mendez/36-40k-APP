package com.acme.operations.security;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// Spring atiende las rutas de esta clase y serializa los valores devueltos como JSON.
@RestController
// Prefijo común de las rutas; se combina con el mapping de cada método.
@RequestMapping("/api/v1/auth")
public class AuthController {
    // Servicio al que el controlador delega las reglas y operaciones de negocio.
    private final AuthService service;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public AuthController(AuthService service) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.service = service;
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/login")
    // Entrega credenciales al servicio y devuelve los tokens si son válidas. Spring convierte y valida el JSON antes
    // de entrar.
    public AuthDtos.Tokens login(@Valid @RequestBody AuthDtos.Login request) {
        // Entrega credenciales al servicio y devuelve los tokens si son válidas.
        return service.login(request.username(), request.password());
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/refresh")
    // Solicita rotación de tokens; el servicio valida la sesión y rechaza reutilizaciones. Spring convierte y valida
    // el JSON antes de entrar.
    public AuthDtos.Tokens refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        // Solicita rotación de tokens; el servicio valida la sesión y rechaza reutilizaciones.
        return service.refresh(request.refreshToken());
    }

    // Expone una acción HTTP POST; el servicio determina los cambios que se realizan.
    @PostMapping("/logout")
    // Si termina correctamente, Spring responde 204 sin cuerpo; las excepciones siguen su propio manejo.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Cierra la familia identificada por sid, obtenido del JWT autenticado.
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        // Cierra la familia identificada por sid, obtenido del JWT autenticado.
        service.logout(jwt.getClaimAsString("sid"));
    }

    // Cambia la contraseña de la identidad autenticada y deja sin validez sus sesiones anteriores.
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthDtos.ChangePassword request) {
        service.changePassword(jwt.getSubject(), request);
    }

    // Actualiza el perfil del usuario autenticado.
    @PutMapping("/user")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AuthDtos.UpdateUser request) {

        // Utiliza la identidad del JWT, no la del body.
        service.updateUser(jwt.getSubject(), request);
    }
}
