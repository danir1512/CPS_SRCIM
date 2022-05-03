package Resource;

import Utilities.DFInteraction;
import jade.core.Agent;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import Libraries.IResource;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetResponder;

import static Utilities.Constants.DFSERVICE_RESOURCE;
import static Utilities.Constants.ONTOLOGY_NEGOTIATE_RESOURCE;

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
    Boolean available;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.description = (String) args[1];
        this.available = true;

        //Load hw lib
        try {
            String className = "Libraries." + (String) args[2];
            Class<?> cls = Class.forName(className);
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
        this.addBehaviour(new contractNetResponder(this, MessageTemplate.MatchPerformative(ACLMessage.CFP)));
        this.addBehaviour(new fipaResponder(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));
    }

    /*
     * Contract Net Responder
     * */
    private class contractNetResponder extends ContractNetResponder {

        public contractNetResponder (Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        protected ACLMessage handleCfp (ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {
            System.out.println(myAgent.getLocalName() + ": Processing CFP message.");
            ACLMessage msg = cfp.createReply();

            if(available) {
                msg.setPerformative(ACLMessage.PROPOSE);
                msg.setContent(Integer.toString(((int) (Math.random() * 100))));
                System.out.println("PROPOSE sent to " + cfp.getSender().getLocalName() + " from " + myAgent.getLocalName());
            }
            else {
                msg.setPerformative(ACLMessage.REFUSE);
                System.out.println("REFUSE sent to " + cfp.getSender().getLocalName() + " from " + myAgent.getLocalName());
            }

            msg.setOntology(ONTOLOGY_NEGOTIATE_RESOURCE);

            return msg;
        }

        protected ACLMessage handleAcceptProposal (ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
            System.out.println(myAgent.getLocalName() + ": Proposal accepted.");
            ACLMessage msg = cfp.createReply();
            msg.setPerformative(ACLMessage.INFORM);
            msg.setContent(location);
            available = false;

            return msg;
        }
    }

    /*
    * AchieveREResponder
    * */
    private class fipaResponder extends AchieveREResponder {

        public fipaResponder(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {
            System.out.println(myAgent.getLocalName() + ": Preparing result of REQUEST");
            ACLMessage msg = request.createReply();
            msg.setPerformative(ACLMessage.AGREE);
            return msg;
        }

        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {
            System.out.println(myAgent.getLocalName() + ": Preparing result of REQUEST");
            myLib.executeSkill(request.getContent());
            ACLMessage msg = request.createReply();
            msg.setPerformative(ACLMessage.INFORM);
            available = true;
            return msg;
        }
    }

    @Override
    protected void takeDown() {
        super.takeDown(); 
    }
}