package br.edu.unijui.gca.api.pipeline.application.ports;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;

public interface ExecutionEventPublisher {
    void publishExecution(SmartContractPayloadDto payload);
    void publishOutcome(SmartContractExecution execution);
}
