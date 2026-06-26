package br.edu.unijui.gca.api.dtos.filter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseFilterDto {
    private int page;
    private int pageSize;
    private String orderBy;
    private String orderDirection;
}
