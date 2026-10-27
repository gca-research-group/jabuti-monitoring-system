package br.edu.unijui.gca.api.metadata.adapters.http;

import br.edu.unijui.gca.api.dtos.BaseFilterDto;
import br.edu.unijui.gca.api.dtos.FindAllResponseDto;
import br.edu.unijui.gca.api.interfaces.CrudMapper;
import br.edu.unijui.gca.api.shared.application.CrudService;
import br.edu.unijui.gca.api.utils.PageBuilder;
import jakarta.validation.Valid;

import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/** HTTP boundary with independent input and output contracts. */
public abstract class CrudResource<
        Entity,
        ID,
        FilterDto extends BaseFilterDto,
        CreateRequest, UpdateRequest, Response> {

    protected abstract CrudMapper<Entity, CreateRequest, UpdateRequest, Response> mapper();

    protected abstract CrudService<Entity, ID, FilterDto, CreateRequest, UpdateRequest, Response> service();

    @GetMapping
    public FindAllResponseDto<List<Response>> findAll(@Valid FilterDto filterDto) {
        Slice<Entity> data = service().findAll(filterDto, PageBuilder.from(filterDto));
        return FindAllResponseDto.<List<Response>>builder()
                .data(data.getContent().stream().map(mapper()::toDto).toList())
                .hasMore(data.hasNext())
                .build();
    }

    @GetMapping("/{id}")
    public Response findById(@PathVariable ID id) {
        Entity entity = service().findById(id);
        return mapper().toDto(entity);
    }

    @PostMapping
    public Response create(@Valid @RequestBody CreateRequest dto) {
        Entity entity = service().create(dto);
        return mapper().toDto(entity);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable ID id, @Valid @RequestBody UpdateRequest dto) {
        Entity entity = service().update(id, dto);
        return mapper().toDto(entity);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable ID id) {
        service().remove(id);
    }
}
