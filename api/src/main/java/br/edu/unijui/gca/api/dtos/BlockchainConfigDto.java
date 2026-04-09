package br.edu.unijui.gca.api.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockchainConfigDto {
    private String field;
    private String type;
    private String description;
}
