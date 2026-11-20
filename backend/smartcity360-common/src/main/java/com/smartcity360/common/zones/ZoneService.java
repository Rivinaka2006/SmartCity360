package com.smartcity360.common.zones;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ZoneService {
    ZoneView create(ZoneCreateCommand command);
    ZoneView get(UUID id);
    List<ZoneView> list(int offset, int limit);
    List<ZoneView> findParentOptions(String type, String search, int offset, int limit);
    List<ComplaintLocationView> complaintLocations(String search, int offset, int limit);
    List<ZoneView> path(UUID id);
    ZoneTreeView tree(UUID id);
    ZoneView update(UUID id, ZoneUpdateCommand command);
    ZoneView deactivate(UUID id);
    PowerOperationView shutdown(UUID id);
    PowerOperationView restore(UUID id);
    UsageView usage(UUID id, OffsetDateTime from, OffsetDateTime to);
    List<ZoneCommandView> commands(UUID id, int offset, int limit);
}
