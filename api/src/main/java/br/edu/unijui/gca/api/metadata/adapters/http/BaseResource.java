package br.edu.unijui.gca.api.metadata.adapters.http;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.dtos.BaseFilterDto;
import br.edu.unijui.gca.api.interfaces.IMapper;
import br.edu.unijui.gca.api.shared.application.BaseService;

/** Transitional HTTP boundary for endpoints that still share one DTO. */
public abstract class BaseResource<Entity, ID, FilterDto extends BaseFilterDto, EntityDto extends BaseDto<ID>>
        extends CrudResource<Entity, ID, FilterDto, EntityDto, EntityDto, EntityDto> {
    protected abstract IMapper<Entity, EntityDto> mapper();
    protected abstract BaseService<Entity, ID, FilterDto, EntityDto> service();
}
