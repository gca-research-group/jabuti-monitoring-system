package br.edu.unijui.gca.api.services;


import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.exceptions.*;
import br.edu.unijui.gca.api.mappers.BlockchainMapper;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Component
public class SmartContractQueueInboundService {
    private final SmartContractService smartContractService;

    private final SmartContractExecutionService smartContractExecutionService;

    private final BlockchainService blockchainService;

    private final BlockchainMapper blockchainMapper;

    private final SmartContractMapper smartContractMapper;

    private final SmartContractExecutionMapper smartContractExecutionMapper;

    private final AmqpTemplate amqpTemplate;

    @RabbitListener(queues = {QueueNames.INBOUND_QUEUE})
    public void process(SmartContractQueueInboundEventDto event) {
        OffsetDateTime consumedAt = OffsetDateTime.now(ZoneOffset.UTC);

        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(event.getId());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_CONSUMED, consumedAt.toString());
        timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());
        smartContractExecution.setTimestamps(timestamps);
        smartContractExecution.setStatus(SmartContractExecutionStatus.PROCESSING);

        try {
            smartContractExecutionService.update(smartContractExecution);

            Blockchain blockchain;

            try {
                blockchain = blockchainService.findById(event.getBlockchainId());
            } catch (ResourceNotFoundException e) {
                throw new BlockchainNotFoundException();
            }

            SmartContract smartContract;

            try {
                smartContract = smartContractService.findById(event.getSmartContractId());
            } catch (ResourceNotFoundException e) {
                throw new SmartContractNotFoundException();
            }

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
                    .blockchain(blockchainMapper.toDto(blockchain))
                    .smartContract(smartContractMapper.toDto(smartContract))
                    .clauseName(event.getClauseName())
                    .clauseArguments(event.getClauseArguments())
                    .build();

            smartContractExecution.setPayload(payload);

            timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSED, consumedAt.toString());
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                    QueueNames.MAIN_EXCHANGE,
                    QueueNames.EXECUTION_ROUTING_KEY,
                    payload
            );
        } catch (Exception exception) {
            timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSED, consumedAt.toString());
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            smartContractExecution.setResult(exception.getMessage());
            smartContractExecution.setStatus(SmartContractExecutionStatus.FAILED);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                    QueueNames.MAIN_EXCHANGE,
                    QueueNames.OUTBOUND_ROUTING_KEY,
                    smartContractExecutionMapper.toDto(smartContractExecution)
            );

            throw new AmqpRejectAndDontRequeueException(exception);
        }
    }
}
