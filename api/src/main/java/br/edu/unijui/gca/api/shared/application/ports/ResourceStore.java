package br.edu.unijui.gca.api.shared.application.ports;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ResourceStore<Entity, ID, Filter> {
    Slice<Entity> findAll(Filter filter, Pageable pageable);
    Entity findById(ID id);
    Entity save(Entity entity);
    void remove(ID id);
}
