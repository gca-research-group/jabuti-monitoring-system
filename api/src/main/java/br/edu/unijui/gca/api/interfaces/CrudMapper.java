package br.edu.unijui.gca.api.interfaces;

import org.mapstruct.MappingTarget;

/** Mapping boundary for independently evolving write and read contracts. */
public interface CrudMapper<Entity, CreateRequest, UpdateRequest, Response> {
    Entity toEntity(CreateRequest request);
    void updateEntity(UpdateRequest request, @MappingTarget Entity entity);
    Response toDto(Entity entity);
}
