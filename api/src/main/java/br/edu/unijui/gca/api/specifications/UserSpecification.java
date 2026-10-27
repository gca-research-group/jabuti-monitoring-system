package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.identity.adapters.http.dto.UserFilter;
import br.edu.unijui.gca.api.entities.User;
import org.springframework.stereotype.Component;


@Component
public class UserSpecification extends BaseSpecification<User, UserFilter> {
    @Override
    protected Class<UserFilter> getFilterClass() {
        return UserFilter.class;
    }
}
