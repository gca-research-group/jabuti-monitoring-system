package br.edu.unijui.gca.api.identity.application.ports;

import br.edu.unijui.gca.api.dtos.user.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import java.util.Optional;

public interface UserStore extends ResourceStore<User, Long, UserFilterDto> {
    Optional<User> findByEmail(String email);
}
