package br.edu.unijui.gca.api.shared.application;

import br.edu.unijui.gca.api.interfaces.CrudMapper;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public abstract class CrudService<Entity, ID, Filter, CreateRequest, UpdateRequest, Response> {
    protected abstract ResourceStore<Entity, ID, Filter> store();
    protected abstract CrudMapper<Entity, CreateRequest, UpdateRequest, Response> mapper();

    public Slice<Entity> findAll(Filter filter, Pageable pageable) {
        return store().findAll(filter, pageable);
    }

    public Entity findById(ID id) {
        return store().findById(id);
    }

    public void remove(ID id) {
        store().remove(id);
    }

    public Entity create(CreateRequest request) {
        return store().save(mapper().toEntity(request));
    }

    public Entity update(ID id, UpdateRequest request) {
        Entity entity = findById(id);
        mapper().updateEntity(request, entity);
        return update(entity);
    }

    public Entity update(Entity entity) {
        return store().save(entity);
    }
}
