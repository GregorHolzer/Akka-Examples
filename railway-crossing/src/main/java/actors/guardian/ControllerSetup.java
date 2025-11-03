package actors.guardian;

import actors.controller.Controller;
import actors.controller_api.ControllerAPI;
import actors.gate.Gate;
import actors.light_machine.LightMachine;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.http.javadsl.Http;
import akka.persistence.typed.PersistenceId;

import java.util.List;

public class ControllerSetup extends AbstractBehavior<Receptionist.Listing> implements ComponentSetup {

    public final static String componentSuffix = "_Controller";

    public final String crossingId;

    private final String crossinName;

    private ActorRef<LightMachine.LightMachineCommand> lightMachine;

    private ActorRef<Gate.GateCommand> gate;

    private ActorRef<Controller.ControllerCommand> controller;

    private final ServiceKey<Gate.GateCommand> gateServiceKey;

    private final ServiceKey<LightMachine.LightMachineCommand> lightMachineServiceKey;

    private final ServiceKey<Controller.ControllerCommand> controllerServiceKey;

    public static Behavior<Receptionist.Listing> create(String crossingId) {
        return Behaviors.setup(context -> new ControllerSetup(context, crossingId));
    }

    private ControllerSetup(ActorContext<Receptionist.Listing> context, String crossingId) {
        super(context);
        this.crossingId = crossingId;
        this.crossinName = crossingId + componentSuffix;
        gateServiceKey = ServiceKey.create(Gate.GateCommand.class, crossingId + GateSetup.componentSuffix);
        lightMachineServiceKey = ServiceKey.create(LightMachine.LightMachineCommand.class, crossingId + LightMachineSetup.componentSuffix);
        controllerServiceKey = ServiceKey.create(Controller.ControllerCommand.class, crossinName);
        getContext().getSystem().receptionist().tell(Receptionist.subscribe(gateServiceKey, getContext().getSelf()));
        getContext().getSystem().receptionist().tell(Receptionist.subscribe(lightMachineServiceKey, getContext().getSelf()));
        context.getLog().info("Controller subscribed to ServiceKeys: {}, {}",  gateServiceKey, lightMachineServiceKey);
    }

    @Override
    public Receive<Receptionist.Listing> createReceive() {
        return newReceiveBuilder()
                .onMessage(Receptionist.Listing.class, this::onListing)
                .build();
    }

        private Behavior<Receptionist.Listing> onListing(Receptionist.Listing listing) {

        if(listing.isForKey(gateServiceKey)){
            List<ActorRef<Gate.GateCommand>> availableGates = listing.getServiceInstances(gateServiceKey).stream().toList();
            gate = checkInstances(getContext(), availableGates, Gate.GateCommand.class);
        }
        if(listing.isForKey(lightMachineServiceKey)){
            List<ActorRef<LightMachine.LightMachineCommand>> availableLightMachines = listing.getServiceInstances(lightMachineServiceKey).stream().toList();
            lightMachine = checkInstances(getContext(), availableLightMachines, LightMachine.LightMachineCommand.class);
        }
        if(gate != null && lightMachine != null && controller == null) {
            controller = getContext().spawn(Controller.create(PersistenceId.ofUniqueId(controllerServiceKey.toString()), gate, lightMachine), String.format("%s", crossinName));
            getContext().getSystem().receptionist().tell(Receptionist.register(controllerServiceKey, controller));
            getContext().getLog().info("Controller registered with ServiceKey: {}",  controllerServiceKey);
            setupAPI();
        }
        return Behaviors.same();
    }

    private void setupAPI(){
        ControllerAPI api = new ControllerAPI(controller);
        int httpPort = getContext().getSystem().settings()
                .config()
                .getInt("akka.http.server.default-http-port");

        Http.get(getContext().getSystem())
                .newServerAt("localhost", httpPort)
                .bind(api.createRoutes());
        getContext().getLog().info("Controller of Service {} API bound to {}:{}",crossingId, getContext().getSystem().address(), httpPort);
    }
}
