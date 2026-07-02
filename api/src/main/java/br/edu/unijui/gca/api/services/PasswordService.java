package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.exceptions.InvalidPasswordException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PasswordService {
    private final PasswordEncoder passwordEncoder;

    public void validatePassword(String password, String encodedPassword) {
        boolean isValid = passwordEncoder.matches(password, encodedPassword);

        if (!isValid) {
            throw new InvalidPasswordException();
        }
    }
}
