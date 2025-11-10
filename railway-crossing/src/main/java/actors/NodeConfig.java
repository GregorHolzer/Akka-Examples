package actors;

import service.ServiceLocation;

public record NodeConfig(
  String crossingId,
  ComponentType componentType,
  ServiceLocation service_location,
  String remote_service_name
) {}
