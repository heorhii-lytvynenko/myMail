package com.mymail.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public abstract class SoftDeletable extends Modifable {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    public void delete(String deletedBy) {
        if (isDeleted()) {
            throw new IllegalStateException("Entity is already deleted");
        }

        if (deletedBy == null || deletedBy.isBlank()) {
            throw new IllegalArgumentException("deletedBy must be not blank");
        }

        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
