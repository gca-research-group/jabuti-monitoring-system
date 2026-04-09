package br.edu.unijui.gca.api.interfaces;

public interface IMapper<Entity, Dto> {
    Entity toEntity(Entity entity, Dto dto);
    Entity toEntity(Dto dto);
    Dto toDto(Entity entity);
}
