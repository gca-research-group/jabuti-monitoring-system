package br.edu.unijui.gca.api.shared.adapters.persistence;

import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.interfaces.IRepository;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import br.edu.unijui.gca.api.specifications.ISpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public abstract class BasePersistenceAdapter<Entity, ID, Filter> implements ResourceStore<Entity, ID, Filter> {
    protected abstract IRepository<Entity, ID> repository();
    protected abstract ISpecification<Entity, Filter> specification();

    @Override
    public Slice<Entity> findAll(Filter filter, Pageable pageable) {
        var spec = specification().build(filter);
        return repository().findBy(spec, query -> query.slice(pageable));
    }

    @Override
    public Entity findById(ID id) {
        return repository().findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    public Entity save(Entity entity) {
        return repository().save(entity);
    }

    @Override
    public void remove(ID id) {
        repository().deleteById(id);
    }
}
