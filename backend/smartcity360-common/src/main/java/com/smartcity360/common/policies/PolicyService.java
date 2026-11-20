package com.smartcity360.common.policies;

import java.util.*;

public interface PolicyService {
    PolicyView create(PolicyCreateCommand command);
    PolicyView get(UUID id);
    List<PolicyView> list(int offset, int limit);
    PolicyVersionView createVersion(UUID policyId, PolicyVersionCreateCommand command);
    List<PolicyVersionView> listVersions(UUID policyId, int offset, int limit);
    PolicyVersionView validateVersion(UUID policyId, UUID versionId);
    PolicyVersionView activateVersion(UUID policyId, UUID versionId);
    PolicyView deactivate(UUID policyId);
    PolicyEvaluationView evaluate(UUID policyId, PolicyEvaluationCommand command);
    List<PolicyEvaluationView> listEvaluations(UUID policyId, int offset, int limit);
}
