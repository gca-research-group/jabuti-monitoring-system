package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainConfigDto;
import br.edu.unijui.gca.api.dtos.blockchain.BlockchainDto;
import br.edu.unijui.gca.api.dtos.blockchain.BlockchainFilterDto;
import br.edu.unijui.gca.api.dtos.blockchain.BlockchainPlatformDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.interfaces.IMapper;
import br.edu.unijui.gca.api.mappers.BlockchainMapper;
import br.edu.unijui.gca.api.services.BaseService;
import br.edu.unijui.gca.api.services.BlockchainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController()
@RequestMapping("/blockchain")
public class BlockchainResource extends BaseResource<Blockchain, UUID, BlockchainFilterDto, BlockchainDto> {

    private final BlockchainService service;

    private final BlockchainMapper mapper;

    @Override
    protected IMapper<Blockchain, BlockchainDto> mapper() {
        return mapper;
    }

    @Override
    protected BaseService<Blockchain, UUID, BlockchainFilterDto, BlockchainDto> service() {
        return service;
    }

    @GetMapping("/config")
    public List<BlockchainConfigDto> config(@RequestParam(name = "platform", required = false) BlockchainPlatform platform) {
        return service.config(platform);
    }

    @GetMapping("/platforms")
    public List<BlockchainPlatformDto> platforms() {
        return service.platforms();
    }
}
