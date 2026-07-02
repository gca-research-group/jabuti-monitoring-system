package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.dtos.user.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.mappers.UserMapper;
import br.edu.unijui.gca.api.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController()
@RequestMapping("/user")
public class UserResource extends BaseResource<User, Long, UserFilterDto, UserDto>{
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
