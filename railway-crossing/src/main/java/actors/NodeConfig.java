package actors;

import java.util.List;
import service.ServiceLocation;

public record NodeConfig(
  List<CrossingConfig> crossings,
  ServiceLocation service_location,
  String remote_service_name,
  String nats_server_addr,
  int nats_server_port,
  String export_server_addr,
  int export_server_port
) {}
