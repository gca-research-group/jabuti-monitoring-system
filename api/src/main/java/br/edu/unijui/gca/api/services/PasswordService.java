package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.exceptions.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    @Autowired
    private PasswordEncoder passwordEncoder;

    public void validatePassword(String password, String encodedPassword) {
        boolean isValid = passwordEncoder.matches(password, encodedPassword);

        if (!isValid) {
            throw new InvalidPasswordException();
        }
    }
}
