package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.identity.adapters.http.dto.*;
import br.edu.unijui.gca.api.interfaces.CrudMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper extends CrudMapper<User, CreateUserRequest, UpdateUserRequest, UserResponse> {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "photo", source = "photo")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "remarks", source = "remarks")
    User toEntity(CreateUserRequest request);

    default void updateEntity(UpdateUserRequest request, @MappingTarget User user) {
        request.name().ifSupplied(user::setName);
        request.email().ifSupplied(user::setEmail);
        request.photo().ifSupplied(user::setPhoto);
        request.status().ifSupplied(user::setStatus);
        request.remarks().ifSupplied(user::setRemarks);
    }
}
