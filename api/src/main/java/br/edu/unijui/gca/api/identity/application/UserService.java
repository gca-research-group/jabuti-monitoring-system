package br.edu.unijui.gca.api.identity.application;

import br.edu.unijui.gca.api.shared.application.BaseService;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.dtos.user.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.mappers.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Profile({"intake", "metadata-management"})
@Service
public class UserService  extends BaseService<
        User,
        Long,
        UserFilterDto,
        UserDto> {

    private final UserStore store;

    private final UserMapper mapper;

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
