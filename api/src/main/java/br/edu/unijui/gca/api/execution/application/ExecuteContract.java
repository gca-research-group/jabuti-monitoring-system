package br.edu.unijui.gca.api.execution.application;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public class ExecuteContract {

    private final ExecutionStore store;
    private final BlockchainStore blockchains;
    private final ContractInvoker invoker;
    private final ExecutionEventPublisher publisher;

    public ExecuteContract(ExecutionStore store, BlockchainStore blockchains,
                           ContractInvoker invoker, ExecutionEventPublisher publisher) {
        this.store = store;
        this.blockchains = blockchains;
        this.invoker = invoker;
        this.publisher = publisher;
    }

    public void process(SmartContractPayloadDto payload) {
        SmartContractExecution smartContractExecution = store.findById(payload.getId());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        try {
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_CONSUMED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());
            store.update(smartContractExecution);

            Blockchain blockchain = blockchains.findById(payload.getBlockchainId());

            String result = invoker.invoke(blockchain, payload);

            smartContractExecution.setResult(result);

            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setStatus(SmartContractExecutionStatus.PROCESSED);
            store.update(smartContractExecution);

            publisher.publishOutcome(smartContractExecution);
        } catch(Exception exception) {
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            smartContractExecution.setResult(exception.getMessage());
            smartContractExecution.setStatus(SmartContractExecutionStatus.FAILED);
            store.update(smartContractExecution);

            publisher.publishOutcome(smartContractExecution);

            throw new ExecutionRejectedException(exception);
        }
    }
}
