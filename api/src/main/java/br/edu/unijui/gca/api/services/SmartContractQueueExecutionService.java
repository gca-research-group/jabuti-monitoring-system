package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.factories.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Slf4j
@Component
public class SmartContractQueueExecutionService {

    private final BlockchainConnectionFactory blockchainConnectionFactory;

    private final SmartContractExecutionService smartContractExecutionService;

    private final SmartContractExecutionMapper smartContractExecutionMapper;

    private final ObjectMapper objectMapper;

    private final AmqpTemplate amqpTemplate;

    @RabbitListener(queues = {QueueNames.EXECUTION_QUEUE})
    public void process(SmartContractPayloadDto payload) {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(payload.getId());

        try {
            smartContractExecution.consumed(SmartContractExecutionEvent.EXECUTION_QUEUE_CONSUMED);
            smartContractExecution.processing(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSING);
            smartContractExecutionService.update(smartContractExecution);

            var parameters = objectMapper.convertValue(payload.getBlockchain().getParameters(),
                    blockchainConnectionFactory.getConfigType(payload.getBlockchain().getPlatform()));

            var service = blockchainConnectionFactory.getInstance(payload.getBlockchain().getPlatform());

            var connection = service.getConnection(payload.getBlockchain().getId().toString(), parameters);

            String result = service.invoke(connection,
                    parameters,
                    payload.getSmartContract().getName(),
                    payload.getClauseName(),
                    payload.getClauseArguments());

            smartContractExecution.setResult(result);
            smartContractExecution.processed(SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSED);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

            smartContractExecution.published(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED);
            smartContractExecutionService.update(smartContractExecution);
        } catch(Exception exception) {
            smartContractExecution.failed(exception);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

            smartContractExecution.published(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED);
            smartContractExecutionService.update(smartContractExecution);

            throw new AmqpRejectAndDontRequeueException(exception);
        }
    }
}
