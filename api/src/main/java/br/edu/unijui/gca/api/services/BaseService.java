package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.interfaces.IMapper;
import br.edu.unijui.gca.api.interfaces.IRepository;
import br.edu.unijui.gca.api.specifications.ISpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;


@RequiredArgsConstructor
public abstract class BaseService<
        Entity,
        ID,
        FilterDto,
        EntityDto extends BaseDto<ID>> {

    protected abstract IRepository<Entity, ID> repository();

    protected abstract ISpecification<Entity, FilterDto> specification();

    protected abstract IMapper<Entity, EntityDto> mapper();

    public Slice<Entity> findAll(FilterDto dto, Pageable pageable) {
        var spec = specification().build(dto);
        return repository().findBy(spec, query -> query.slice(pageable));
    }

    public Entity findById(ID id) {
        return repository().findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    public void remove(ID id) {
        repository().deleteById(id);
    }

    public Entity create(EntityDto dto) {
        Entity entity = mapper().toEntity(dto);
        return repository().save(entity);
    }

    public Entity update(EntityDto dto) {
        Entity entity = repository().findById(dto.getId()).orElseThrow(ResourceNotFoundException::new);
        mapper().updateEntity(dto, entity);
        return update(entity);
    }

    public Entity update(Entity entity) {
        return repository().save(entity);
    }
}
