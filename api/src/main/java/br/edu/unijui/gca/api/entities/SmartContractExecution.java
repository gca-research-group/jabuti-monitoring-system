package br.edu.unijui.gca.api.entities;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
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
    @Column
    protected UUID id;

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

    @Enumerated(EnumType.STRING)
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
}