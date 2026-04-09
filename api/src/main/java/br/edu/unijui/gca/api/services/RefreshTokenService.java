package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.entities.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
    @Autowired
    private JwtService jwtService;

    private Cookie createCookie(String jrt) {
        Cookie cookie = new Cookie("jrt", jrt);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24);

        return cookie;
    }

    public void setRefreshToken(HttpServletResponse response, User user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        response.addCookie(createCookie(refreshToken));
    }

    public void removeRefreshToken(HttpServletResponse response) {
        response.addCookie(createCookie(null));
    }
}
