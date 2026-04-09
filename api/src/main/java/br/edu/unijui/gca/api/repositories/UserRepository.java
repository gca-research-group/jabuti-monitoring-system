package br.edu.unijui.gca.api.repositories;

import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.interfaces.IRepository;

public interface UserRepository extends IRepository<User, Long> {
    User findByEmail(String email);
}
