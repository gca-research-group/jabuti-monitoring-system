package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.dtos.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.mappers.UserMapper;
import br.edu.unijui.gca.api.repositories.UserRepository;
import br.edu.unijui.gca.api.specifications.UserSpecification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService  extends BaseService<
        User,
        Long,
        UserDto,
        UserFilterDto,
        UserRepository,
        UserSpecification,
        UserMapper> {

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
