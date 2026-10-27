package br.edu.unijui.gca.api.interfaces;

/** Transitional mapper for endpoints that still share one DTO. */
public interface IMapper<Entity, EntityDto> extends CrudMapper<Entity, EntityDto, EntityDto, EntityDto> {
}
