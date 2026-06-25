package br.edu.unijui.gca.api.repositories;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.interfaces.IRepository;

import java.util.Optional;

public interface UserRepository extends IRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
