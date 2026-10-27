package br.edu.unijui.gca.api.identity.application.ports;

import br.edu.unijui.gca.api.identity.adapters.http.dto.UserFilter;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import java.util.Optional;

public interface UserStore extends ResourceStore<User, Long, UserFilter> {
    Optional<User> findByEmail(String email);
}
