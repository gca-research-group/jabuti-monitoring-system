package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.factories.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Component
public class SmartContractQueueExecutionService {

    private final BlockchainConnectionFactory blockchainConnectionFactory;

    private final SmartContractExecutionService smartContractExecutionService;

    private final SmartContractExecutionMapper smartContractExecutionMapper;

    private final SmartContractService smartContractService;

    private final BlockchainService blockchainService;

    private final ObjectMapper objectMapper;

    private final AmqpTemplate amqpTemplate;

    @RabbitListener(queues = {QueueNames.EXECUTION_QUEUE})
    public void process(SmartContractPayloadDto payload) {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(payload.getId());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        try {
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_CONSUMED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecutionService.update(smartContractExecution);

            Blockchain blockchain = blockchainService.findById(payload.getBlockchainId());

            var parameters = objectMapper.convertValue(blockchain.getParameters(),
                    blockchainConnectionFactory.getConfigType(blockchain.getPlatform()));

            var service = blockchainConnectionFactory.getInstance(blockchain.getPlatform());

            var connectionId = blockchain.getId().toString() + "_" + blockchain.getUpdatedAt().toEpochMilli();

            var connection = service.getConnection(connectionId, parameters);

            SmartContract smartContract  = smartContractService.findById(payload.getSmartContractId());

            String result = service.invoke(connection,
                    parameters,
                    smartContract.getName(),
                    payload.getClauseName(),
                    payload.getClauseArguments());

            smartContractExecution.setResult(result);

            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setStatus(SmartContractExecutionStatus.PROCESSED);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

        } catch(Exception exception) {
            timestamps.put(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            smartContractExecution.setResult(exception.getMessage());
            smartContractExecution.setStatus(SmartContractExecutionStatus.FAILED);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

            throw new AmqpRejectAndDontRequeueException(exception);
        }
    }
}
