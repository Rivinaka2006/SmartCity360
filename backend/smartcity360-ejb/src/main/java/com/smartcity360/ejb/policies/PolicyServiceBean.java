package com.smartcity360.ejb.policies;

import com.smartcity360.common.policies.*;
import com.smartcity360.ejb.persistence.model.*;
import com.smartcity360.ejb.persistence.repository.*;
import com.smartcity360.ejb.policies.domain.interpreter.*;
import com.smartcity360.ejb.security.SecurityAuditService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.*;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import jakarta.json.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Stateless
@DeclareRoles({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF", "RESIDENT"})
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class PolicyServiceBean implements PolicyService {
    private static final Set<String> READ_ROLES = Set.of("ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF");
    private static final Set<String> MANAGE_ROLES = Set.of("ADMIN", "CITY_MANAGER");
    private static final Pattern POLICY_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,49}");
    private static final DslLimits LIMITS = DslLimits.defaults();
    private static final PolicyVariableRegistry VARIABLES = PolicyVariableRegistry.standard();

    @Inject
    PolicyRepository policies;
    @Inject
    PolicyVersionRepository versions;
    @Inject
    PolicyEvaluationRepository evaluations;
    @Inject
    UserRepository users;
    @Inject
    UserRoleRepository userRoles;
    @Inject
    SecurityAuditService audit;
    @Resource
    SessionContext session;

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PolicyView create(PolicyCreateCommand command) {
        UserEntity actor = actor();
        requireAnyRole(actor, MANAGE_ROLES);
        if (command == null) throw error("REQUEST_INVALID");
        String code = policyCode(command.policyCode());
        policies.lockPolicyCode(code);
        if (policies.findByPolicyCode(code).isPresent()) throw error("POLICY_CODE_EXISTS");
        PolicyEntity policy = new PolicyEntity();
        policy.setId(UUID.randomUUID());
        policy.setPolicyCode(code);
        policy.setName(text(command.name(), 150, "POLICY_NAME_INVALID"));
        policy.setDomain(parseDomain(command.domain()));
        policy.setDescription(optionalText(command.description(), 4000, "POLICY_DESCRIPTION_INVALID"));
        policy.setStatus(PolicyEntity.Status.DRAFT);
        policy.setCreatedBy(actor);
        policy.setCreatedAt(OffsetDateTime.now());
        policy.setUpdatedAt(policy.getCreatedAt());
        policies.persist(policy);
        audit("POLICY_CREATED", actor, policy.getId(), "code=" + code);
        return view(policy);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public PolicyView get(UUID id) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        PolicyEntity policy = requirePolicy(id);
        if (!canManage(actor) && policy.getStatus() != PolicyEntity.Status.ACTIVE) throw error("POLICY_NOT_FOUND");
        return view(policy);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    public List<PolicyView> list(int offset, int limit) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        page(offset, limit);
        return policies.listOrdered(!canManage(actor), offset, limit).stream().map(PolicyServiceBean::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PolicyVersionView createVersion(UUID policyId, PolicyVersionCreateCommand command) {
        UserEntity actor = actor();
        requireAnyRole(actor, MANAGE_ROLES);
        PolicyEntity policy = lockPolicy(policyId);
        if (policy.getStatus() == PolicyEntity.Status.INACTIVE) throw error("POLICY_INACTIVE");
        if (command == null || command.dslExpression() == null || command.dslExpression().isBlank())
            throw error("DSL_EMPTY");
        PolicyVersionEntity version = new PolicyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setPolicy(policy);
        try {
            version.setVersionNo(versions.nextVersionNumber(policy.getId()));
        } catch (IllegalStateException exception) {
            throw error("VERSION_LIMIT");
        }
        version.setDslVersion("1");
        version.setDslExpression(command.dslExpression());
        Validation validation = validate(policy, command.dslExpression());
        version.setValid(validation.valid());
        version.setValidationError(validation.error());
        version.setActive(false);
        version.setCreatedBy(actor);
        version.setCreatedAt(OffsetDateTime.now());
        versions.persist(version);
        audit("POLICY_VERSION_CREATED", actor, policy.getId(),
                "version=" + version.getVersionNo() + ",valid=" + version.isValid());
        return view(version);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public List<PolicyVersionView> listVersions(UUID policyId, int offset, int limit) {
        UserEntity actor = actor();
        requireAnyRole(actor, MANAGE_ROLES);
        page(offset, limit);
        requirePolicy(policyId);
        return versions.findByPolicy(policyId, offset, limit).stream().map(PolicyServiceBean::view).toList();
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PolicyVersionView validateVersion(UUID policyId, UUID versionId) {
        UserEntity actor = actor();
        requireAnyRole(actor, MANAGE_ROLES);
        PolicyEntity policy = lockPolicy(policyId);
        if (policy.getStatus() == PolicyEntity.Status.INACTIVE) throw error("POLICY_INACTIVE");
        PolicyVersionEntity version = requireVersion(policyId, versionId);
        Validation validation = validate(policy, version.getDslExpression());
        version.setValid(validation.valid());
        version.setValidationError(validation.error());
        audit("POLICY_VERSION_VALIDATED", actor, policy.getId(),
                "version=" + version.getVersionNo() + ",valid=" + version.isValid());
        return view(version);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PolicyVersionView activateVersion(UUID policyId, UUID versionId) {
        UserEntity actor = actor();
        requireAnyRole(actor, MANAGE_ROLES);
        PolicyEntity policy = lockPolicy(policyId);
        if (policy.getStatus() == PolicyEntity.Status.INACTIVE) throw error("POLICY_INACTIVE");
        PolicyVersionEntity target = requireVersion(policyId, versionId);
        if (!target.isValid()) throw error("VERSION_NOT_VALID");
        List<PolicyVersionEntity> activeVersions = versions.findActiveForUpdate(policyId);
        if (activeVersions.size() == 1 && activeVersions.get(0).getId().equals(target.getId())) return view(target);
        for (PolicyVersionEntity active : activeVersions) active.setActive(false);
        // EclipseLink may otherwise order target=true before old=false, which would correctly trip
        // PostgreSQL's partial unique index even inside this serialized transaction.
        versions.flushChanges();
        target.setActive(true);
        versions.flushChanges();
        policy.setStatus(PolicyEntity.Status.ACTIVE);
        policy.setUpdatedAt(OffsetDateTime.now());
        audit("POLICY_VERSION_ACTIVATED", actor, policy.getId(), "version=" + target.getVersionNo());
        return view(target);
    }

    @Override
    @RolesAllowed("ADMIN")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PolicyView deactivate(UUID policyId) {
        UserEntity actor = actor();
        requireRole(actor, "ADMIN");
        PolicyEntity policy = lockPolicy(policyId);
        for (PolicyVersionEntity active : versions.findActiveForUpdate(policyId)) active.setActive(false);
        policy.setStatus(PolicyEntity.Status.INACTIVE);
        policy.setUpdatedAt(OffsetDateTime.now());
        audit("POLICY_DEACTIVATED", actor, policy.getId(), "status=INACTIVE");
        return view(policy);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER", "SUPERVISOR", "LOCAL_STAFF"})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PolicyEvaluationView evaluate(UUID policyId, PolicyEvaluationCommand command) {
        UserEntity actor = actor();
        requireAnyRole(actor, READ_ROLES);
        if (command == null) throw error("REQUEST_INVALID");
        PolicyEntity policy = policies.findByIdForRead(requiredId(policyId, "POLICY_NOT_FOUND"))
                .orElseThrow(() -> error("POLICY_NOT_FOUND"));
        if (policy.getStatus() != PolicyEntity.Status.ACTIVE) throw error("POLICY_NOT_ACTIVE");
        PolicyVersionEntity version = versions.findActive(policyId).orElseThrow(() -> error("ACTIVE_VERSION_NOT_FOUND"));
        long started = System.nanoTime();
        PolicyEvaluationEntity entity = new PolicyEvaluationEntity();
        entity.setId(UUID.randomUUID());
        entity.setPolicyVersion(version);
        entity.setEvaluatedBy(actor);
        entity.setContextJson(contextJson(command.context()));
        entity.setEvaluatedAt(OffsetDateTime.now());
        String explanation = null;
        try {
            Expression expression = parser(policy).parse(version.getDslExpression());
            EvaluationContext context = EvaluationContext.create(domain(policy), command.context(), VARIABLES, LIMITS);
            EvaluationResult result = expression.interpret(context);
            entity.setStatus(PolicyEvaluationEntity.Status.SUCCESS);
            entity.setResult(result.outcome());
            explanation = explanationJson(result.explanation());
        } catch (DslValidationException exception) {
            entity.setStatus(PolicyEvaluationEntity.Status.ERROR);
            entity.setResult(null);
            entity.setErrorMessage(exception.code());
        }
        entity.setDurationMs(duration(started));
        evaluations.persist(entity);
        return view(entity, policy.getId(), explanation);
    }

    @Override
    @RolesAllowed({"ADMIN", "CITY_MANAGER"})
    public List<PolicyEvaluationView> listEvaluations(UUID policyId, int offset, int limit) {
        UserEntity actor = actor();
        requireAnyRole(actor, MANAGE_ROLES);
        page(offset, limit);
        requirePolicy(policyId);
        return evaluations.findByPolicy(policyId, offset, limit).stream()
                .map(entity -> view(entity, policyId, null)).toList();
    }

    private Validation validate(PolicyEntity policy, String source) {
        try {
            parser(policy).parse(source);
            return new Validation(true, null);
        } catch (DslValidationException exception) {
            if (Set.of("SOURCE_LENGTH_LIMIT", "TOKEN_LIMIT", "AST_NODE_LIMIT", "AST_DEPTH_LIMIT").contains(exception.code()))
                throw error(exception.code());
            return new Validation(false, exception.code() + " at position " + Math.max(0, exception.position()));
        }
    }

    private DslParser parser(PolicyEntity policy) {
        return new DslParser(VARIABLES, LIMITS, domain(policy));
    }

    private static PolicyDomain domain(PolicyEntity policy) {
        return PolicyDomain.valueOf(policy.getDomain().name());
    }

    private PolicyEntity requirePolicy(UUID id) {
        return policies.findById(requiredId(id, "POLICY_NOT_FOUND")).orElseThrow(() -> error("POLICY_NOT_FOUND"));
    }

    private PolicyEntity lockPolicy(UUID id) {
        return policies.findByIdForUpdate(requiredId(id, "POLICY_NOT_FOUND")).orElseThrow(() -> error("POLICY_NOT_FOUND"));
    }

    private PolicyVersionEntity requireVersion(UUID policyId, UUID versionId) {
        return versions.findByPolicyAndId(policyId, requiredId(versionId, "VERSION_NOT_FOUND")).orElseThrow(() -> error("VERSION_NOT_FOUND"));
    }

    private UserEntity actor() {
        UserEntity user = users.findByEmail(session.getCallerPrincipal().getName()).orElseThrow(() -> error("CALLER_NOT_FOUND"));
        if (user.getStatus() != UserEntity.Status.ACTIVE) throw error("CALLER_INACTIVE");
        return user;
    }

    private void requireRole(UserEntity user, String role) {
        if (!userRoles.hasRole(user.getId(), role)) throw error("FORBIDDEN");
    }

    private void requireAnyRole(UserEntity user, Set<String> roles) {
        if (roles.stream().noneMatch(role -> userRoles.hasRole(user.getId(), role))) throw error("FORBIDDEN");
    }

    private boolean canManage(UserEntity user) {
        return MANAGE_ROLES.stream().anyMatch(role -> userRoles.hasRole(user.getId(), role));
    }

    private void audit(String action, UserEntity actor, UUID id, String detail) {
        audit.recordApplicationEvent(action, actor.getId(), "POLICY", id,
                Json.createObjectBuilder().add("status", "SUCCESS").add("detail", detail).build().toString());
    }

    private static PolicyView view(PolicyEntity p) {
        return new PolicyView(p.getId(), p.getPolicyCode(), p.getName(), p.getDomain().name(), p.getDescription(), p.getStatus().name(), p.getCreatedBy().getId(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static PolicyVersionView view(PolicyVersionEntity v) {
        return new PolicyVersionView(v.getId(), v.getPolicy().getId(), v.getVersionNo(), v.getDslVersion(), v.getDslExpression(), v.isValid(), v.getValidationError(), v.isActive(), v.getCreatedBy().getId(), v.getCreatedAt());
    }

    private static PolicyEvaluationView view(PolicyEvaluationEntity e, UUID policyId, String explanation) {
        return new PolicyEvaluationView(e.getId(), policyId, e.getPolicyVersion().getId(), e.getPolicyVersion().getVersionNo(), e.getEvaluatedBy() == null ? null : e.getEvaluatedBy().getId(), e.getStatus().name(), e.getResult(), e.getErrorMessage(), e.getDurationMs() == null ? 0 : e.getDurationMs(), e.getEvaluatedAt(), explanation);
    }

    private static String contextJson(Map<String, ?> values) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean bool) builder.add(entry.getKey(), bool);
            else if (value instanceof BigDecimal decimal) builder.add(entry.getKey(), decimal);
            else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                builder.add(entry.getKey(), ((Number) value).longValue());
            else if (value instanceof Number number) builder.add(entry.getKey(), new BigDecimal(number.toString()));
            else if (value instanceof String string) builder.add(entry.getKey(), string);
            else if (value == null) builder.addNull(entry.getKey());
            else throw error("CONTEXT_TYPE_MISMATCH");
        }
        String json = builder.build().toString();
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > LIMITS.maxContextPayloadBytes())
            throw error("CONTEXT_PAYLOAD_LIMIT");
        return json;
    }

    private static String explanationJson(EvaluationExplanation explanation) {
        JsonObjectBuilder builder = Json.createObjectBuilder().add("operator", explanation.operator()).add("evaluated", explanation.evaluated()).add("detail", explanation.detail());
        if (explanation.outcome() == null) builder.addNull("outcome");
        else builder.add("outcome", explanation.outcome());
        JsonArrayBuilder children = Json.createArrayBuilder();
        explanation.children().forEach(child -> children.add(Json.createReader(new java.io.StringReader(explanationJson(child))).readObject()));
        return builder.add("children", children).build().toString();
    }

    private static int duration(long started) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (System.nanoTime() - started) / 1_000_000));
    }

    private static PolicyEntity.Domain parseDomain(String value) {
        try {
            return PolicyEntity.Domain.valueOf(text(value, 12, "POLICY_DOMAIN_INVALID").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw error("POLICY_DOMAIN_INVALID");
        }
    }

    private static String policyCode(String value) {
        String code = text(value, 50, "POLICY_CODE_INVALID").toUpperCase(Locale.ROOT);
        if (!POLICY_CODE.matcher(code).matches()) throw error("POLICY_CODE_INVALID");
        return code;
    }

    private static String text(String value, int max, String code) {
        if (value == null || value.isBlank()) throw error(code);
        String stripped = value.strip();
        if (stripped.length() > max) throw error(code);
        return stripped;
    }

    private static String optionalText(String value, int max, String code) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        if (stripped.length() > max) throw error(code);
        return stripped;
    }

    private static UUID requiredId(UUID id, String code) {
        if (id == null) throw error(code);
        return id;
    }

    private static void page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw error("PAGINATION_INVALID");
    }

    private static PolicyApplicationException error(String code) {
        return new PolicyApplicationException(code);
    }

    private record Validation(boolean valid, String error) {
    }
}
