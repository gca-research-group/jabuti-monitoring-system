package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.services.SmartContractExecutionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController()
@RequestMapping("/smart-contract-execution")
public class SmartContractExecutionResource extends BaseResource<SmartContractExecution, UUID, SmartContractExecutionDto, SmartContractExecutionFilterDto, SmartContractExecutionService> {

    @DeleteMapping()
    public void removeAll() {
        this.service.removeAll();
    }
}
