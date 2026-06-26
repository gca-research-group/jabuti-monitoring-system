package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.filter.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import org.springframework.stereotype.Component;


@Component
public class UserSpecification extends BaseSpecification<User, UserFilterDto> {
    @Override
    protected Class<UserFilterDto> getFilterClass() {
        return UserFilterDto.class;
    }
}
