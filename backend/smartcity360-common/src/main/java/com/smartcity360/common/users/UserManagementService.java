package com.smartcity360.common.users;

import java.util.List;
import java.util.UUID;

public interface UserManagementService {
    List<ManagedUserView> list(String search, int offset, int limit);

    ManagedUserView create(UserCreateCommand command);

    ManagedUserView updateRoles(UUID userId, UserRoleUpdateCommand command);

    ManagedUserView updateStatus(UUID userId, UserStatusUpdateCommand command);
}
