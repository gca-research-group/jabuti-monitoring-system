package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.BlockchainConfigDto;
import br.edu.unijui.gca.api.dtos.BlockchainDto;
import br.edu.unijui.gca.api.dtos.BlockchainFilterDto;
import br.edu.unijui.gca.api.dtos.BlockchainPlatformDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.services.BlockchainService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController()
@RequestMapping("/blockchain")
public class BlockchainResource extends BaseResource<Blockchain, UUID, BlockchainDto, BlockchainFilterDto, BlockchainService> {
    @GetMapping("/config")
    public List<BlockchainConfigDto> config(@RequestParam(name = "platform", required = false) BlockchainPlatform platform) {
        return service.config(platform);
    }

    @GetMapping("/platforms")
    public List<BlockchainPlatformDto> platforms() {
        return service.platforms();
    }
}
