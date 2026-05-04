package br.edu.unijui.gca.api.entities;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "smart_contract_executions")
public class SmartContractExecution {

    @Id
    @UuidGenerator
    @Column
    private UUID id;

    @Column
    private String executionId;

    @Column
    private String groupId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private SmartContractPayloadDto payload;

    @Column
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(nullable = false)
    private String status;

    @Column
    private String remarks;

    /*
     * Inbound queue tracking
     */

    @Column
    private Instant inboundQueuePublishedAt;

    @Column
    private Instant inboundQueueConsumedAt;

    @Column
    private Instant inboundQueueProcessingStartedAt;

    @Column
    private Instant inboundQueueProcessedAt;

    /*
     * Execution queue tracking
     */

    @Column
    private Instant executionQueuePublishedAt;

    @Column
    private Instant executionQueueConsumedAt;

    @Column
    private Instant executionQueueProcessingStartedAt;

    @Column
    private Instant executionQueueProcessedAt;

    /*
     * Outbound queue tracking
     */

    @Column
    private Instant outboundQueuePublishedAt;

    @Column
    private Instant outboundQueueConsumedAt;

    @Column
    private Instant outboundQueueProcessingStartedAt;

    @Column
    private Instant outboundQueueProcessedAt;

    @CreatedDate
    @Column
    private Instant createdAt;

    @LastModifiedDate
    @Column
    private Instant updatedAt;
}