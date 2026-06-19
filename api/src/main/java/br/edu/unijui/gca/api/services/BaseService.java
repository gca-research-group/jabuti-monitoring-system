package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.dtos.FindAllResponseDto;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.mappers.FindAllResponseMapper;
import br.edu.unijui.gca.api.interfaces.IMapper;
import br.edu.unijui.gca.api.interfaces.IRepository;
import br.edu.unijui.gca.api.specifications.ISpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public abstract class BaseService<
        Entity,
        IdentifierType,
        EntityDto extends BaseDto<IdentifierType>,
        FilterDto,
        Repository extends IRepository<Entity, IdentifierType>,
        Specification extends ISpecification<Entity, FilterDto>,
        Mapper extends IMapper<Entity, EntityDto>> {

    @Autowired
    protected Repository repository;

    @Autowired
    protected Specification specification;

    @Autowired
    protected Mapper mapper;

    public FindAllResponseDto<List<Entity>> findAll(FilterDto dto, Pageable pageable) {
        Slice<Entity> data = repository.findBy(specification.build(dto), query -> query.slice(pageable));
        return FindAllResponseMapper.fromSlice(data);
    }

    public Entity findById(IdentifierType id) {
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    public void remove(IdentifierType id) {
        repository.deleteById(id);
    }

    public Entity create(EntityDto dto) {
        return repository.save(mapper.toEntity(dto));
    }

    public Entity update(IdentifierType id, EntityDto dto) {
        return repository.save(mapper.toEntity(findById(id), dto));
    }

    public Entity update(Entity entity) {
        return repository.save(entity);
    }
}
