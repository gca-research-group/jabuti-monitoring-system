package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserSpecification  implements ISpecification<User, UserFilterDto> {
    @Override
    public Specification<User> build(UserFilterDto filter) {
        return null;
    }
}
