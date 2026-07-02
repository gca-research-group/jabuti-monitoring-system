package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.AuthResponseDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.ExpiredTokenException;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final PasswordService passwordService;

    private final UserService userService;

    private final JwtService jwtService;

    private final RefreshTokenService refreshTokenService;

    public AuthResponseDto authenticate(String email, String password, HttpServletResponse response) {
        User user = userService.findByEmail(email).orElseThrow(ResourceNotFoundException::new);

        passwordService.validatePassword(password, user.getPassword());

        refreshTokenService.setRefreshToken(response, user);

        String accessToken = jwtService.generateAccessToken(user);

        return AuthResponseDto.from(user, accessToken);
    }

    public AuthResponseDto refresh(HttpServletResponse response, String token) {
        try {
            String email = jwtService.getSubject(token);
            User user = userService.findByEmail(email).orElseThrow(ResourceNotFoundException::new);

            if(jwtService.isTokenInvalid(token, user)) {
                throw new InvalidTokenException();
            }

            refreshTokenService.setRefreshToken(response, user);
            String accessToken = jwtService.generateAccessToken(user);

            return AuthResponseDto.from(user, accessToken);
        } catch (ExpiredTokenException e) {
            refreshTokenService.removeRefreshToken(response);
            throw e;
        }
    }
}
