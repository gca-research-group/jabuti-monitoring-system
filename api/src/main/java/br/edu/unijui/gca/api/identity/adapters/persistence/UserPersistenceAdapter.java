package br.edu.unijui.gca.api.identity.adapters.persistence;

import br.edu.unijui.gca.api.identity.adapters.http.dto.UserFilter;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;
import br.edu.unijui.gca.api.repositories.UserRepository;
import br.edu.unijui.gca.api.specifications.UserSpecification;
import br.edu.unijui.gca.api.shared.adapters.persistence.BasePersistenceAdapter;
import lombok.RequiredArgsConstructor;
import java.util.Optional;

@RequiredArgsConstructor
public class UserPersistenceAdapter extends BasePersistenceAdapter<User, Long, UserFilter> implements UserStore {
    private final UserRepository repository;
    private final UserSpecification specification;

    @Override
    protected UserRepository repository() {
        return repository;
    }

    @Override
    protected UserSpecification specification() {
        return specification;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
