package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.AuthResponseDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.ExpiredTokenException;
import br.edu.unijui.gca.api.exceptions.InvalidPasswordException;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.UserNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public AuthResponseDto authenticate(String email, String password, HttpServletResponse response) {
        User user = userService.findByEmail(email);

        if (user == null) {
            throw new UserNotFoundException();
        }

        boolean isValid = passwordEncoder.matches(password, user.getPassword());

        if (!isValid) {
            throw new InvalidPasswordException();
        }

        refreshTokenService.setRefreshToken(response, user);

        String accessToken = jwtService.generateAccessToken(user);

        return AuthResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .accessToken(accessToken)
                .build();
    }

    public AuthResponseDto refresh(HttpServletResponse response, String token) {
        try {
            String email = jwtService.getSubject(token);
            User user = userService.findByEmail(email);

            if(!jwtService.isTokenValid(token, user)) {
                throw new InvalidTokenException();
            }

            refreshTokenService.setRefreshToken(response, user);
            String accessToken = jwtService.generateAccessToken(user);

            return AuthResponseDto.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .accessToken(accessToken)
                    .build();
        } catch (ExpiredTokenException e) {
            refreshTokenService.removeRefreshToken(response);
            throw e;
        }
    }
}
