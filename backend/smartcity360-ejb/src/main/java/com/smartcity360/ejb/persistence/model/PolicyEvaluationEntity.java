package com.smartcity360.ejb.persistence.model;

import com.smartcity360.ejb.persistence.converter.JsonbConverter;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "policy_evaluations")
public class PolicyEvaluationEntity extends AbstractUuidEntity {
    public enum Status {SUCCESS, ERROR}

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false)
    private PolicyVersionEntity policyVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_by")
    private UserEntity evaluatedBy;
    @Convert(converter = JsonbConverter.class)
    @Column(name = "context_json", nullable = false, columnDefinition = "jsonb")
    private String contextJson = "{}";
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;
    @Column(name = "result")
    private Boolean result;
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
    @Column(name = "duration_ms")
    private Integer durationMs;
    @Column(name = "evaluated_at", nullable = false)
    private OffsetDateTime evaluatedAt = OffsetDateTime.now();

    public PolicyVersionEntity getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(PolicyVersionEntity v) {
        policyVersion = v;
    }

    public UserEntity getEvaluatedBy() {
        return evaluatedBy;
    }

    public void setEvaluatedBy(UserEntity v) {
        evaluatedBy = v;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String v) {
        contextJson = v;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status v) {
        status = v;
    }

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean v) {
        result = v;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String v) {
        errorMessage = v;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer v) {
        durationMs = v;
    }

    public OffsetDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(OffsetDateTime v) {
        evaluatedAt = v;
    }
}
