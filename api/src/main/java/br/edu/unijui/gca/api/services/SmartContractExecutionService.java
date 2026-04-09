package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SmartContractExecutionService  extends BaseService<
        SmartContractExecution,
        UUID,
        SmartContractExecutionDto,
        SmartContractExecutionFilterDto,
        SmartContractExecutionRepository,
        SmartContractExecutionSpecification,
        SmartContractExecutionMapper> {
    public void removeAll() {
        repository.deleteAll();
    }
}