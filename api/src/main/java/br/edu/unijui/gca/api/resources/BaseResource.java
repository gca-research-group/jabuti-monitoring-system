package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.BaseDto;
import br.edu.unijui.gca.api.dtos.FindAllResponseDto;
import br.edu.unijui.gca.api.services.BaseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public abstract class BaseResource<
        Entity,
        IdentifierType,
        EntityDto extends BaseDto<IdentifierType>,
        FilterDto,
        Service extends BaseService<Entity, IdentifierType, EntityDto, FilterDto, ?, ?, ?>> {

    @Autowired
    protected Service service;

    @GetMapping
    public FindAllResponseDto<List<Entity>> findAll(FilterDto filterDto, Pageable pageable) {
        return service.findAll(filterDto, pageable);
    }

    @GetMapping("/{id}")
    public Entity findById(@PathVariable IdentifierType id) {
        return service.findById(id);
    }

    @PostMapping
    public Entity create(@Valid @RequestBody EntityDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public Entity update(@PathVariable IdentifierType id, @Valid @RequestBody EntityDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable IdentifierType id) {
        service.remove(id);
    }
}
