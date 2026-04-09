package br.edu.unijui.gca.api.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name="users")
public class User {
    @Id
    @GeneratedValue
    @Column
    Long id;

    @Column
    String name;

    @Column
    String email;

    @Column
    String photo;

    @Column
    String password;

    @Column
    Boolean status;

    @Column
    String remarks;

    @CreatedBy
    @Column
    Long createdById;

    @CreatedDate
    @Column
    Instant createdAt;

    @LastModifiedBy
    @Column
    Long updatedById;

    @LastModifiedDate
    @Column
    Instant updatedAt;
}
