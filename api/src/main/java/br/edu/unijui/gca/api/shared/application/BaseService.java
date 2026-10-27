package br.edu.unijui.gca.api.shared.application;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.interfaces.IMapper;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public abstract class BaseService<Entity, ID, Filter, EntityDto extends BaseDto<ID>> {
    protected abstract ResourceStore<Entity, ID, Filter> store();
    protected abstract IMapper<Entity, EntityDto> mapper();

    public Slice<Entity> findAll(Filter filter, Pageable pageable) {
        return store().findAll(filter, pageable);
    }

    public Entity findById(ID id) {
        return store().findById(id);
    }

    public void remove(ID id) {
        store().remove(id);
    }

    public Entity create(EntityDto dto) {
        Entity entity = mapper().toEntity(dto);
        return store().save(entity);
    }

    public Entity update(EntityDto dto) {
        Entity entity = findById(dto.getId());
        mapper().updateEntity(dto, entity);
        return update(entity);
    }

    public Entity update(Entity entity) {
        return store().save(entity);
    }
}
