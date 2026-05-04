package br.edu.unijui.gca.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SmartContractExecutionDto extends BaseDto<UUID> {
    private String executionId;
    private String groupId;

    private SmartContractPayloadDto payload;
    private Map<String, Object> metadata;
    private String result;
    private String status;

    /*
     * Inbound queue tracking
     */

    private Instant inboundQueuePublishedAt;

    private Instant inboundQueueConsumedAt;

    private Instant inboundQueueProcessingStartedAt;

    private Instant inboundQueueProcessedAt;

    /*
     * Execution queue tracking
     */

    private Instant executionQueuePublishedAt;

    private Instant executionQueueConsumedAt;

    private Instant executionQueueProcessingStartedAt;

    private Instant executionQueueProcessedAt;

    /*
     * Outbound queue tracking
     */

    private Instant outboundQueuePublishedAt;

    private Instant outboundQueueConsumedAt;

    private Instant outboundQueueProcessingStartedAt;

    private Instant outboundQueueProcessedAt;
}
