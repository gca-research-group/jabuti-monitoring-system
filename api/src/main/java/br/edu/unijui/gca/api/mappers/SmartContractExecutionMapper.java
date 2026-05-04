package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.springframework.stereotype.Component;

@Component
public class SmartContractExecutionMapper implements IMapper<SmartContractExecution, SmartContractExecutionDto> {
    @Override
    public SmartContractExecution toEntity(SmartContractExecution smartContractExecution, SmartContractExecutionDto dto) {
        smartContractExecution.setInboundQueuePublishedAt(dto.getInboundQueuePublishedAt());
        smartContractExecution.setInboundQueueConsumedAt(dto.getInboundQueueConsumedAt());
        smartContractExecution.setInboundQueueProcessingStartedAt(dto.getInboundQueueProcessingStartedAt());
        smartContractExecution.setInboundQueueProcessedAt(dto.getInboundQueueProcessedAt());

        smartContractExecution.setExecutionQueuePublishedAt(dto.getExecutionQueuePublishedAt());
        smartContractExecution.setExecutionQueueConsumedAt(dto.getExecutionQueueConsumedAt());
        smartContractExecution.setExecutionQueueProcessingStartedAt(dto.getExecutionQueueProcessingStartedAt());
        smartContractExecution.setExecutionQueueProcessedAt(dto.getExecutionQueueProcessedAt());

        smartContractExecution.setOutboundQueuePublishedAt(dto.getOutboundQueuePublishedAt());
        smartContractExecution.setOutboundQueueConsumedAt(dto.getOutboundQueueConsumedAt());
        smartContractExecution.setOutboundQueueProcessingStartedAt(dto.getOutboundQueueProcessingStartedAt());
        smartContractExecution.setOutboundQueueProcessedAt(dto.getOutboundQueueProcessedAt());

        smartContractExecution.setExecutionId(dto.getExecutionId());
        smartContractExecution.setGroupId(dto.getGroupId());

        smartContractExecution.setMetadata(dto.getMetadata());
        smartContractExecution.setRemarks(dto.getRemarks());
        smartContractExecution.setResult(dto.getResult());
        smartContractExecution.setStatus(dto.getStatus());
        smartContractExecution.setPayload(dto.getPayload());

        return smartContractExecution;
    }

    @Override
    public SmartContractExecution toEntity(SmartContractExecutionDto dto) {
        return toEntity(new SmartContractExecution(), dto);
    }

    @Override
    public SmartContractExecutionDto toDto(SmartContractExecution smartContractExecution) {
        return SmartContractExecutionDto.builder()
                .id(smartContractExecution.getId())
                .executionId(smartContractExecution.getExecutionId())
                .groupId(smartContractExecution.getGroupId())
                .payload(smartContractExecution.getPayload())
                .metadata(smartContractExecution.getMetadata())
                .result(smartContractExecution.getResult())
                .status(smartContractExecution.getStatus())
                .inboundQueuePublishedAt(smartContractExecution.getInboundQueuePublishedAt())
                .inboundQueueConsumedAt(smartContractExecution.getInboundQueueConsumedAt())
                .inboundQueueProcessingStartedAt(smartContractExecution.getInboundQueueProcessingStartedAt())
                .inboundQueueProcessedAt(smartContractExecution.getInboundQueueProcessedAt())
                .executionQueuePublishedAt(smartContractExecution.getExecutionQueuePublishedAt())
                .executionQueueConsumedAt(smartContractExecution.getExecutionQueueConsumedAt())
                .executionQueueProcessingStartedAt(smartContractExecution.getExecutionQueueProcessingStartedAt())
                .executionQueueProcessedAt(smartContractExecution.getExecutionQueueProcessedAt())
                .outboundQueuePublishedAt(smartContractExecution.getOutboundQueuePublishedAt())
                .outboundQueueConsumedAt(smartContractExecution.getOutboundQueueConsumedAt())
                .outboundQueueProcessingStartedAt(smartContractExecution.getOutboundQueueProcessingStartedAt())
                .outboundQueueProcessedAt(smartContractExecution.getOutboundQueueProcessedAt())
                .build();
    }
}
