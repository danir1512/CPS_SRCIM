package Transport;

import Utilities.Constants;
import Utilities.DFInteraction;
import Resource.ResourceAgent;
import jade.core.Agent;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import Libraries.ITransport;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;

/**
 *
 * @author Ricardo Silva Peres <ricardo.peres@uninova.pt>
 */
public class TransportAgent extends Agent {

    String id;
    ITransport myLib;
    String description;
    String[] associatedSkills;

    boolean available;
    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.description = (String) args[1];
        this.available = true;

        //Load hw lib
        try {
            String className = "Libraries." + (String) args[2];
            Class cls = Class.forName(className);
            Object instance;
            instance = cls.newInstance();
            myLib = (ITransport) instance;
            System.out.println(instance);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(TransportAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        myLib.init(this);
        this.associatedSkills = myLib.getSkills();
        System.out.println("Transport Deployed: " + this.id + " Executes: " + Arrays.toString(associatedSkills));

        // TO DO: Register in DF
        try {
            DFInteraction.RegisterInDF(this, this.associatedSkills, this.description);
        } catch (FIPAException e) {
            Logger.getLogger(ResourceAgent.class.getName()).log(Level.SEVERE, null, e);
        }

        // TO DO: Add responder behaviour/s
        this.addBehaviour(new TransportResponder(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));

    }

    private class TransportResponder extends AchieveREResponder{

        String[] productLocations;

        public TransportResponder(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException{
            System.out.println(myAgent.getLocalName() + ": Received transportation request from " + request.getSender().getLocalName());
            ACLMessage msg = request.createReply();

            if(available){
                productLocations = request.getContent().split(Constants.TOKEN);
                msg.setPerformative(ACLMessage.AGREE);
                available = false;
            } else {
                msg.setPerformative(ACLMessage.REFUSE);
                System.out.println(myAgent.getLocalName() + ": sent REFUSE to: " + request.getSender().getLocalName());
            }

            return msg;
        }

        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) {
            System.out.println(myAgent.getLocalName() + ": Preparing result of REQUEST. Starting to make MOVE task.");
            //System.out.println(productLocations[0] + " " + productLocations[1] + " " + request.getSender().getLocalName());
            myLib.executeMove(productLocations[0], productLocations[1], request.getSender().getLocalName());

            ACLMessage msg = request.createReply();
            msg.setPerformative(ACLMessage.INFORM);
            msg.setContent(productLocations[1]);

            System.out.println(myAgent.getLocalName() + ": Performed MOVE operation for " + request.getSender().getLocalName());
            available = true;
            return msg;
        }

    }

    @Override
    protected void takeDown() {
        super.takeDown();
    }
}