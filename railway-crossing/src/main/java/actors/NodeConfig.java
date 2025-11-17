package actors;

import service.ServiceLocation;

import java.util.List;

public record NodeConfig(
        List<CrossingConfig> crossings,
        ServiceLocation service_location,
        String remote_service_name
) {}
