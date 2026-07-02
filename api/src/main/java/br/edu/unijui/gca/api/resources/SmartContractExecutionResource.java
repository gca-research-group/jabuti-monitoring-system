package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.services.SmartContractExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController()
@RequestMapping("/smart-contract-execution")
public class SmartContractExecutionResource extends BaseResource<
        SmartContractExecution,
        UUID,
        SmartContractExecutionFilterDto,
        SmartContractExecutionDto> {

    private final SmartContractExecutionMapper mapper;

    private final SmartContractExecutionService service;

    @Override
    protected SmartContractExecutionMapper mapper() {
        return mapper;
    }

    @Override
    protected SmartContractExecutionService service() {
        return service;
    }

    @DeleteMapping()
    public void removeAll() {
        this.service.removeAll();
    }

    @PostMapping("/execute")
    public void execute(@RequestBody SmartContractQueueInboundEventDto dto) {
        this.service.execute(dto);
    }
}
