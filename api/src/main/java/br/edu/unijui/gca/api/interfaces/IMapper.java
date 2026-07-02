package br.edu.unijui.gca.api.interfaces;

import org.mapstruct.MappingTarget;

public interface IMapper<Entity, EntityDto> {
    Entity toEntity(EntityDto dto);
    void updateEntity(EntityDto dto, @MappingTarget Entity entity);
    EntityDto toDto(Entity entity);
}
