package Resource;

import Utilities.DFInteraction;
import jade.core.Agent;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import Libraries.IResource;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetResponder;

import static Utilities.Constants.DFSERVICE_RESOURCE;

/**
 *
 * @author Ricardo Silva Peres <ricardo.peres@uninova.pt>
 */
public class ResourceAgent extends Agent {

    String id;
    IResource myLib;
    String description;
    String[] associatedSkills;
    String location;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.description = (String) args[1];

        //Load hw lib
        try {
            String className = "Libraries." + (String) args[2];
            Class cls = Class.forName(className);
            Object instance;
            instance = cls.newInstance();
            myLib = (IResource) instance;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(ResourceAgent.class.getName()).log(Level.SEVERE, null, ex);
        }

        this.location = (String) args[3];

        myLib.init(this);
        this.associatedSkills = myLib.getSkills();
        System.out.println("Resource Deployed: " + this.id + " Executes: " + Arrays.toString(associatedSkills));

        // TO DO: Register in DF with the corresponding skills as services
        try {
            DFInteraction.RegisterInDF(this, associatedSkills, DFSERVICE_RESOURCE);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        // TO DO: Add responder behaviour/s
        this.addBehaviour(new responder(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));

    }

    /*
    * AchieveREResponder
    * */
    private class responder extends AchieveREResponder{

        public responder(Agent a, MessageTemplate mt){
            super(a, mt);
        }

        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException{
            System.out.println(myAgent.getLocalName() + ": Preparing result of REQUEST");
            ACLMessage msg = request.createReply();
            msg.setPerformative(ACLMessage.INFORM);
            return msg;
        }

        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException{
            System.out.println(myAgent.getLocalName() + ": Preparing result of REQUEST");

            ACLMessage msg = request.createReply();
            msg.setPerformative(ACLMessage.INFORM);
            return msg;
        }
    }

    /*
    * Contract Net Responder
    * */
    private class contractResponder extends ContractNetResponder {

        public contractResponder (Agent a, MessageTemplate mt){
            super(a, mt);
        }

        protected ACLMessage handleCfp (ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
            System.out.print(myAgent.getLocalName() + ": Processing CFP message");
            ACLMessage msg = cfp.createReply();
            msg.setPerformative(ACLMessage.PROPOSE);
            msg.setContent("My Proposal value");
            return msg;
        }

        protected ACLMessage handleAcceptProposal (ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
            System.out.println(myAgent.getLocalName() + ": Preparing result of CFP");
            block(5000);
            ACLMessage msg = cfp.createReply();
            msg.setPerformative(ACLMessage.INFORM);
            return msg;
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown(); 
    }
}
