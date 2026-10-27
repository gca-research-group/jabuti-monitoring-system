package br.edu.unijui.gca.api.identity.adapters.http;

import br.edu.unijui.gca.api.identity.adapters.http.dto.LoginRequest;
import br.edu.unijui.gca.api.identity.adapters.http.dto.LoginResponse;
import br.edu.unijui.gca.api.identity.application.AuthService;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@Profile("metadata-management")
@RestController
@RequestMapping("/auth")
public class AuthResource {

    private final AuthService authService;
    private final TokenOperations tokens;

    @PostMapping("/login")
    public LoginResponse login(@jakarta.validation.Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.authenticate(request.getEmail(), request.getPassword(), new RefreshTokenService(tokens, response));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@CookieValue(name = "jrt", required = false) String jrt, HttpServletResponse response) {
        return authService.refresh(new RefreshTokenService(tokens, response), jrt);
    }
}
