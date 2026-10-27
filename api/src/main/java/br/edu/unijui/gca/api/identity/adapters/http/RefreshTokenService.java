package br.edu.unijui.gca.api.identity.adapters.http;

import br.edu.unijui.gca.api.identity.application.ports.RefreshTokenCookies;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;

import br.edu.unijui.gca.api.entities.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenCookies {
    private final TokenOperations jwtService;
    private final HttpServletResponse response;

    private Cookie createCookie(String jrt) {
        Cookie cookie = new Cookie("jrt", jrt);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24);

        return cookie;
    }

    public void setRefreshToken(User user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        response.addCookie(createCookie(refreshToken));
    }

    public void removeRefreshToken() {
        response.addCookie(createCookie(null));
    }
}
