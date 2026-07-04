package br.edu.unijui.gca.api.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String keyPrefix;

    @Column(nullable = false, length = 64)
    private String keyHash;

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
