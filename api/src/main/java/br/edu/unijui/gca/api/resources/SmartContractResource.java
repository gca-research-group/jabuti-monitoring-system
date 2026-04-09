package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.SmartContractDto;
import br.edu.unijui.gca.api.dtos.SmartContractFilterDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.services.SmartContractQueueInboundService;
import br.edu.unijui.gca.api.services.SmartContractService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController()
@RequestMapping("/smart-contract")
public class SmartContractResource extends BaseResource<SmartContract, UUID, SmartContractDto, SmartContractFilterDto, SmartContractService> {
    private final SmartContractQueueInboundService smartContractQueueInboundService;

    public SmartContractResource(SmartContractQueueInboundService smartContractQueueInboundService) {
        this.smartContractQueueInboundService = smartContractQueueInboundService;
    }

    @PostMapping("/execute")
    public void execute(@RequestBody SmartContractQueueInboundEventDto dto) {
        smartContractQueueInboundService.process(dto);
    }
}
