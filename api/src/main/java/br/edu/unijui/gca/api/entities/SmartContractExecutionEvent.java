package br.edu.unijui.gca.api.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "smart_contract_execution_events")
public class SmartContractExecutionEvent {
    @Id
    @UuidGenerator
    @Column
    private UUID id;

    @Column
    private String name;

    @Column
    private UUID smartContractExecutionId;

    @Column
    private Instant eventAt;

    @CreatedDate
    @Column
    private Instant createdAt;
}
