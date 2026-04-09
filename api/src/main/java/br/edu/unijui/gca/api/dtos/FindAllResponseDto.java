package br.edu.unijui.gca.api.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FindAllResponseDto<T> {
    private Integer pages;
    private Integer page;
    private Boolean hasMore;
    private Long total;
    private T data;
}

