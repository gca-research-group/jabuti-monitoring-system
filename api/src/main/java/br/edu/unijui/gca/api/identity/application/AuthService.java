package br.edu.unijui.gca.api.identity.application;

import br.edu.unijui.gca.api.dtos.AuthResponseDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.ExpiredTokenException;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.identity.application.ports.PasswordVerifier;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;
import br.edu.unijui.gca.api.identity.application.ports.RefreshTokenCookies;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Profile("metadata-management")
@Service
public class AuthService {
    private final PasswordVerifier passwordService;

    private final UserStore userService;

    private final TokenOperations jwtService;


    public AuthResponseDto authenticate(String email, String password, RefreshTokenCookies response) {
        User user = userService.findByEmail(email).orElseThrow(ResourceNotFoundException::new);

        passwordService.validatePassword(password, user.getPassword());

        response.setRefreshToken(user);

        String accessToken = jwtService.generateAccessToken(user);

        return AuthResponseDto.from(user, accessToken);
    }

    public AuthResponseDto refresh(RefreshTokenCookies response, String token) {
        try {
            String email = jwtService.getSubject(token);
            User user = userService.findByEmail(email).orElseThrow(ResourceNotFoundException::new);

            if(jwtService.isTokenInvalid(token, user)) {
                throw new InvalidTokenException();
            }

            response.setRefreshToken(user);
            String accessToken = jwtService.generateAccessToken(user);

            return AuthResponseDto.from(user, accessToken);
        } catch (ExpiredTokenException e) {
            response.removeRefreshToken();
            throw e;
        }
    }
}
