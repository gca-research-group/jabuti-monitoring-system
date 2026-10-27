package br.edu.unijui.gca.api.metadata.adapters.http;

import br.edu.unijui.gca.api.identity.adapters.http.dto.*;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.mappers.UserMapper;
import br.edu.unijui.gca.api.identity.application.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@Profile("metadata-management")
@RestController()
@RequestMapping("/user")
public class UserResource extends CrudResource<User, Long, UserFilter, CreateUserRequest, UpdateUserRequest, UserResponse>{
    private final UserMapper mapper;
    private final UserService service;

    @Override
    protected UserMapper mapper() {
        return mapper;
    }

    @Override
    protected UserService service() {
        return service;
    }
}
