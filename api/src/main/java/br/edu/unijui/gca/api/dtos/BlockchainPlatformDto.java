package br.edu.unijui.gca.api.dtos;

import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockchainPlatformDto {
    private BlockchainPlatform id;
    private BlockchainPlatform name;
    private String image;

}
