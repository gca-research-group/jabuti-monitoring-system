package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.services.SmartContractExecutionService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController()
@RequestMapping("/smart-contract-execution")
public class SmartContractExecutionResource extends BaseResource<SmartContractExecution, UUID, SmartContractExecutionDto, SmartContractExecutionFilterDto, SmartContractExecutionService> {

    @DeleteMapping()
    public void removeAll() {
        this.service.removeAll();
    }

    @PostMapping("/execute")
    public void execute(@RequestBody SmartContractQueueInboundEventDto dto)  {
        this.service.execute(dto);
    }
}
