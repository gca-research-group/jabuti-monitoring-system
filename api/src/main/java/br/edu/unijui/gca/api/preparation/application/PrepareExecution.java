package br.edu.unijui.gca.api.preparation.application;

import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.exceptions.InvalidBlockchainPlatformException;
import br.edu.unijui.gca.api.exceptions.InvalidSmartContractClauseArgumentException;
import br.edu.unijui.gca.api.exceptions.InvalidSmartContractClauseException;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PrepareExecution {

    private final ExecutionStore store;
    private final BlockchainStore blockchains;
    private final SmartContractStore contracts;
    private final ExecutionEventPublisher publisher;

    public PrepareExecution(ExecutionStore store, BlockchainStore blockchains, SmartContractStore contracts, ExecutionEventPublisher publisher) {
        this.store = store;
        this.blockchains = blockchains;
        this.contracts = contracts;
        this.publisher = publisher;
    }

    public void process(SmartContractQueueInboundEventDto event) {
        OffsetDateTime consumedAt = OffsetDateTime.now(ZoneOffset.UTC);

        SmartContractExecution smartContractExecution = store.create(SmartContractExecutionDto.builder().id(event.getId())
                .status(SmartContractExecutionStatus.PENDING)
                .metadata(event.getMetadata())
                .timestamps(event.getTimestamps()).build());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_CONSUMED, consumedAt.toString());
        timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());
        smartContractExecution.setTimestamps(timestamps);
        smartContractExecution.setStatus(SmartContractExecutionStatus.PROCESSING);

        try {
            store.update(smartContractExecution);

            Blockchain blockchain = blockchains.findById(event.getBlockchainId());

            SmartContract smartContract  = contracts.findById(event.getSmartContractId());

            if (blockchain.getPlatform() != smartContract.getBlockchainPlatform()) {
                throw new InvalidBlockchainPlatformException();
            }

            SmartContractClauseDto clause = smartContract
                    .getClauses()
                    .stream()
                    .filter(item -> item.getName().equals(event.getClauseName()))
                    .findFirst()
                    .orElseThrow(InvalidSmartContractClauseException::new);

            if (!event.getClauseArguments().isEmpty()) {
                Set<String> argumentNames = clause.getClauseArguments().stream().map(SmartContractClauseArgumentDto::getName).collect(Collectors.toSet());

                boolean hasInvalidClauseArgument = event.getClauseArguments().stream().anyMatch(item -> !argumentNames.contains(item.getName()));

                if (hasInvalidClauseArgument) {
                    throw new InvalidSmartContractClauseArgumentException();
                }
            }

            var payload = SmartContractPayloadDto
                    .builder()
                    .id(smartContractExecution.getId())
                    .blockchainId(blockchain.getId())
                    .smartContractId(smartContract.getId())
                    .clauseName(event.getClauseName())
                    .clauseArguments(event.getClauseArguments())
                    .build();

            smartContractExecution.setPayload(payload);

            timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSED, consumedAt.toString());
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            store.update(smartContractExecution);

            publisher.publishExecution(payload);
        } catch (Exception exception) {
            timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSED, consumedAt.toString());
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            smartContractExecution.setResult(exception.getMessage());
            smartContractExecution.setStatus(SmartContractExecutionStatus.FAILED);
            store.update(smartContractExecution);

            publisher.publishOutcome(smartContractExecution);

            throw new PreparationRejectedException(exception);
        }
    }
}
