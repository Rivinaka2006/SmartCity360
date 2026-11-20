package com.smartcity360.ejb.persistence.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "policy_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"policy_id", "version_no"}))
public class PolicyVersionEntity extends AbstractUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private PolicyEntity policy;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(name = "dsl_version", nullable = false, length = 12)
    private String dslVersion = "1";
    @Column(name = "dsl_expression", nullable = false, columnDefinition = "text")
    private String dslExpression;
    @Column(name = "is_valid", nullable = false)
    private boolean valid;
    @Column(name = "validation_error", columnDefinition = "text")
    private String validationError;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public PolicyEntity getPolicy() {
        return policy;
    }

    public void setPolicy(PolicyEntity v) {
        policy = v;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int v) {
        versionNo = v;
    }

    public String getDslVersion() {
        return dslVersion;
    }

    public void setDslVersion(String v) {
        dslVersion = v;
    }

    public String getDslExpression() {
        return dslExpression;
    }

    public void setDslExpression(String v) {
        dslExpression = v;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean v) {
        valid = v;
    }

    public String getValidationError() {
        return validationError;
    }

    public void setValidationError(String v) {
        validationError = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        active = v;
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
}
