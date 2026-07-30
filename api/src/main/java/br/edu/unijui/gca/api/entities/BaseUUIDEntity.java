package br.edu.unijui.gca.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseUUIDEntity {

    @Id
    @Column
    protected UUID id;

    @PrePersist
    protected void generateId() {
        if (id == null) {
            id = UUID.ofEpochMillis(System.currentTimeMillis());
        }
    }
}
