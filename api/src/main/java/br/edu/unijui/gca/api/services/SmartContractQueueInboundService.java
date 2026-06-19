package br.edu.unijui.gca.api.services;


import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.*;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.exceptions.*;
import br.edu.unijui.gca.api.mappers.BlockchainMapper;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SmartContractQueueInboundService {
    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private BlockchainMapper blockchainMapper;

    @Autowired
    private SmartContractService smartContractService;

    @Autowired
    private SmartContractMapper smartContractMapper;

    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private SmartContractExecutionMapper smartContractExecutionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(queues = {QueueNames.INBOUND_QUEUE})
    public void process(SmartContractQueueInboundEventDto event) {
        Instant consumedAt = Instant.now();
        /*SmartContractExecutionDto smartContractExecutionDto = SmartContractExecutionDto.builder()
                .status("PENDING")
                .metadata(Map.of("event", event))
                .inboundQueueConsumedAt(consumedAt)
                .inboundQueueProcessingStartedAt(Instant.now())
                .build();
        SmartContractExecution smartContractExecution = smartContractExecutionService.create(smartContractExecutionDto);
                */

        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(event.getId());

        smartContractExecution.setInboundQueueConsumedAt(consumedAt);
        smartContractExecution.setInboundQueueProcessingStartedAt(Instant.now());
        smartContractExecutionService.update(smartContractExecution);

        try {
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

            smartContractExecution.setStatus("QUEUED");
            smartContractExecution.setPayload(payload);
            smartContractExecution.setInboundQueueProcessedAt(Instant.now());

            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                    QueueNames.MAIN_EXCHANGE,
                    QueueNames.EXECUTION_ROUTING_KEY,
                    payload
            );

            smartContractExecution.setExecutionQueuePublishedAt(Instant.now());
            smartContractExecutionService.update(smartContractExecution);
        } catch (Exception ex) {
            smartContractExecution.setStatus("ERROR");
            smartContractExecution.setResult(
                    objectMapper.writeValueAsString(
                            Map.of("error", ex.getMessage())
                    )
            );

            smartContractExecution.setInboundQueueProcessedAt(Instant.now());
            smartContractExecution.setOutboundQueuePublishedAt(Instant.now());

            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                    QueueNames.MAIN_EXCHANGE,
                    QueueNames.OUTBOUND_ROUTING_KEY,
                    smartContractExecutionMapper.toDto(smartContractExecution)
            );

            throw new AmqpRejectAndDontRequeueException(ex);
        }
    }
}
