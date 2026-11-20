package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "policies")
public class PolicyEntity extends AbstractUuidEntity {
    public enum Domain {NOISE, PARKING, GENERAL}

    public enum Status {DRAFT, ACTIVE, INACTIVE}

    @Column(name = "policy_code", nullable = false, unique = true, length = 50)
    private String policyCode;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 12)
    private Domain domain;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.DRAFT;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String v) {
        policyCode = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain v) {
        domain = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        status = v;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity v) {
        createdBy = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        updatedAt = v;
    }
}
