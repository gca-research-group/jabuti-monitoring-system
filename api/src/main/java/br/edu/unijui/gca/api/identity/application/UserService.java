package br.edu.unijui.gca.api.identity.application;

import br.edu.unijui.gca.api.shared.application.CrudService;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;

import br.edu.unijui.gca.api.identity.adapters.http.dto.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.mappers.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Profile({"intake", "metadata-management"})
@Service
public class UserService  extends CrudService<
        User,
        Long,
        UserFilter,
        CreateUserRequest, UpdateUserRequest, UserResponse> {

    private final UserStore store;

    private final UserMapper mapper;
    private final PasswordEncoder passwords;

    @Override
    public User create(CreateUserRequest request) {
        User user = mapper.toEntity(request);
        user.setPassword(passwords.encode(request.getPassword()));
        return store.save(user);
    }

    @Override
    public User update(Long id, UpdateUserRequest request) {
        User user = findById(id);
        mapper.updateEntity(request, user);
        request.password().ifSupplied(value -> user.setPassword(passwords.encode(value)));
        return store.save(user);
    }

    @Override
    protected UserStore store() {
        return store;
    }

    @Override
    protected UserMapper mapper() {
        return mapper;
    }

    public Optional<User> findByEmail(String email) {
        return store.findByEmail(email);
    }
}
