package br.edu.unijui.gca.api.identity.adapters.http;

import br.edu.unijui.gca.api.dtos.AuthRequestDto;
import br.edu.unijui.gca.api.dtos.AuthResponseDto;
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
    public AuthResponseDto login(@RequestBody AuthRequestDto request, HttpServletResponse response) {
        return authService.authenticate(request.email(), request.password(), new RefreshTokenService(tokens, response));
    }

    @PostMapping("/refresh")
    public AuthResponseDto refresh(@CookieValue(name = "jrt", required = false) String jrt, HttpServletResponse response) {
        return authService.refresh(new RefreshTokenService(tokens, response), jrt);
    }
}

