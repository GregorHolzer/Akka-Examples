package actors;

import java.util.List;
import service.ServiceLocation;

public record NodeConfig(
  List<CrossingConfig> crossings,
  ServiceLocation service_location,
  String remote_service_name
) {}
