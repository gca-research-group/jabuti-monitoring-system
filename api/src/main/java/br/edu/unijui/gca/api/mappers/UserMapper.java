package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.UserDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper extends IMapper<User, UserDto> {
}
