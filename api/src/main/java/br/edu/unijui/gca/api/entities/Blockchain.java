package br.edu.unijui.gca.api.entities;

import br.edu.unijui.gca.api.enums.BlockchainPlatform;
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
@Table(name="blockchains")
public class Blockchain {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column
    private UUID id;

    @Column
    private String name;

    @Enumerated(EnumType.STRING)
    @Column
    private BlockchainPlatform platform;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column
    private boolean status;

    @Column
    private String remarks;

    @CreatedDate
    @Column
    private Instant createdAt;

    @LastModifiedDate
    @Column
    private Instant updatedAt;
}
