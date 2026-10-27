package br.edu.unijui.gca.api.shared.application;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.interfaces.IMapper;

/** Compatibility boundary retaining DTO identity and existing update overrides. */
public abstract class BaseService<Entity, ID, Filter, EntityDto extends BaseDto<ID>>
        extends CrudService<Entity, ID, Filter, EntityDto, EntityDto, EntityDto> {
    protected abstract IMapper<Entity, EntityDto> mapper();

    @Override
    public Entity update(ID id, EntityDto dto) {
        dto.setId(id);
        return update(dto);
    }

    public Entity update(EntityDto dto) {
        Entity entity = findById(dto.getId());
        mapper().updateEntity(dto, entity);
        return update(entity);
    }
}
