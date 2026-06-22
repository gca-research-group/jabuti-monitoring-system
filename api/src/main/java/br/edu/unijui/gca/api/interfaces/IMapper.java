package br.edu.unijui.gca.api.interfaces;

import org.mapstruct.MappingTarget;

public interface IMapper<Entity, Dto> {
    Entity toEntity(Dto dto);
    void updateEntity( Dto dto, @MappingTarget Entity entity);
    Dto toDto(Entity entity);
}
