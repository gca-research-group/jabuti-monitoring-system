package br.edu.unijui.gca.api.identity.adapters.security;

import br.edu.unijui.gca.api.identity.application.ports.PasswordVerifier;

import br.edu.unijui.gca.api.exceptions.InvalidPasswordException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Profile("metadata-management")
@Service
public class PasswordService implements PasswordVerifier {
    private final PasswordEncoder passwordEncoder;

    public void validatePassword(String password, String encodedPassword) {
        boolean isValid = passwordEncoder.matches(password, encodedPassword);

        if (!isValid) {
            throw new InvalidPasswordException();
        }
    }
}
