package br.edu.unijui.gca.api.entities;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private SmartContractPayloadDto payload;

    @Column
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<SmartContractExecutionEvent, String> timestamps;

    @Column(nullable = false)
    private SmartContractExecutionStatus status;

    @Column
    private String remarks;

    @CreatedDate
    @Column
    private Instant createdAt;

    @LastModifiedDate
    @Column
    private Instant updatedAt;

    public void published(SmartContractExecutionEvent key) {
        timestamps.put(key, OffsetDateTime.now(ZoneOffset.UTC).toString());
        status = SmartContractExecutionStatus.PUBLISHED;
    }

    public void consumed(SmartContractExecutionEvent key) {
        timestamps.put(key, OffsetDateTime.now(ZoneOffset.UTC).toString());
        status = SmartContractExecutionStatus.CONSUMED;
    }

    public void processing(SmartContractExecutionEvent key) {
        timestamps.put(key, OffsetDateTime.now(ZoneOffset.UTC).toString());
        status = SmartContractExecutionStatus.PROCESSING;
    }

    public void processed(SmartContractExecutionEvent key) {
        timestamps.put(key, OffsetDateTime.now(ZoneOffset.UTC).toString());
        status = SmartContractExecutionStatus.PROCESSED;
    }

    public void failed(Throwable error) {
        status = SmartContractExecutionStatus.FAILED;
        result = error.getMessage();
    }

    public void complete() {
        status = SmartContractExecutionStatus.COMPLETE;
    }
}