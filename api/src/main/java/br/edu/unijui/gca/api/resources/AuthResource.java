package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.AuthRequestDto;
import br.edu.unijui.gca.api.dtos.AuthResponseDto;
import br.edu.unijui.gca.api.services.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthResource {

    private final AuthService authService;

    public AuthResource(
            AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponseDto login(@RequestBody AuthRequestDto request, HttpServletResponse response) {
        return authService.authenticate(request.email(), request.password(), response);
    }

    @PostMapping("/refresh")
    public AuthResponseDto refresh(@CookieValue(name = "jrt", required = false) String jrt, HttpServletResponse response) {
        return authService.refresh(response, jrt);
    }
}

