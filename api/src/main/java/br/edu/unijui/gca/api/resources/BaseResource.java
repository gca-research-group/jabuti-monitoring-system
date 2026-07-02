package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.dtos.BaseFilterDto;
import br.edu.unijui.gca.api.dtos.FindAllResponseDto;
import br.edu.unijui.gca.api.interfaces.IMapper;
import br.edu.unijui.gca.api.services.BaseService;
import br.edu.unijui.gca.api.utils.PageBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RequiredArgsConstructor
public abstract class BaseResource<
        Entity,
        ID,
        FilterDto extends BaseFilterDto,
        EntityDto extends BaseDto<ID>> {

    protected abstract IMapper<Entity, EntityDto> mapper();

    protected abstract BaseService<Entity, ID, FilterDto, EntityDto> service();

    @GetMapping
    public FindAllResponseDto<List<EntityDto>> findAll(@Valid FilterDto filterDto) {
        Slice<Entity> data = service().findAll(filterDto, PageBuilder.from(filterDto));
        return FindAllResponseDto.<List<EntityDto>>builder()
                .data(data.getContent().stream().map(mapper()::toDto).toList())
                .hasMore(data.hasNext())
                .build();
    }

    @GetMapping("/{id}")
    public EntityDto findById(@PathVariable ID id) {
        Entity entity = service().findById(id);
        return mapper().toDto(entity);
    }

    @PostMapping
    public EntityDto create(@Valid @RequestBody EntityDto dto) {
        Entity entity = service().create(dto);
        return mapper().toDto(entity);
    }

    @PutMapping("/{id}")
    public EntityDto update(@PathVariable ID id, @Valid @RequestBody EntityDto dto) {
        dto.setId(id);
        Entity entity = service().update(dto);
        return mapper().toDto(entity);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable ID id) {
        service().remove(id);
    }
}
