package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.springframework.stereotype.Component;

@Component
public class UserMapper implements IMapper<User, UserDto> {
    @Override
    public User toEntity(User user, UserDto dto) {
        return null;
    }

    @Override
    public User toEntity(UserDto dto) {
        return toEntity(new User(), dto);
    }

    @Override
    public UserDto toDto(User user) {
        return null;
    }
}
