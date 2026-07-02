package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.dtos.user.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.mappers.UserMapper;
import br.edu.unijui.gca.api.repositories.UserRepository;
import br.edu.unijui.gca.api.specifications.UserSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserService  extends BaseService<
        User,
        Long,
        UserFilterDto,
        UserDto> {

    private final UserRepository repository;

    private final UserSpecification specification;

    private final UserMapper mapper;

    @Override
    protected UserRepository repository() {
        return repository;
    }

    @Override
    protected UserSpecification specification() {
        return specification;
    }

    @Override
    protected UserMapper mapper() {
        return mapper;
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
