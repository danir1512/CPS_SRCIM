package Product;

import Libraries.SimTransportLibrary;
import Utilities.Constants;
import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import jade.proto.ContractNetInitiator;
import jade.util.Logger;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;

import static Utilities.Constants.ONTOLOGY_EXECUTE_SKILL;
import static Utilities.Constants.SK_MOVE;

/**
 * @author Ricardo Silva Peres <ricardo.peres@uninova.pt>
 */
public class ProductAgent extends Agent {

    String id;
    ArrayList<String> executionPlan = new ArrayList<>();
    // TO DO: Add remaining attributes required for your implementation
    int plan_step;
    String current_pos, next_pos;
    boolean recovery_tried, quality_check;
    AID bestResource, ta; //AID -> agent identifier
    boolean request_agv, transport_done, skill_done, product_in_place;

    /*
    * plan_step ->
    * current_pos ->
    * quality_check ->
    * recovery_tried ->
    * request_agv ->
    * skill_done ->
    * */
    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.executionPlan = this.getExecutionList((String) args[1]); //get the execution plan given the input in the UI
        System.out.println("Product launched: " + this.id + " Requires: " + executionPlan);

        this.plan_step = 0;
        this.current_pos = "Source";
        this.quality_check = true;
        this.recovery_tried = false;
        this.request_agv = false;
        this.skill_done = false;

        // TO DO: Add necessary behaviour/s for the product to control the flow
        // of its own production
        SequentialBehaviour sb = new SequentialBehaviour(); // SequentialBehaviour encadeia vários sub-behaviours que são executados de forma sucessiva
        for (int i = 0; i < executionPlan.size(); i++) {
            sb.addSubBehaviour(new searchResourceAgentInDF(this));
            sb.addSubBehaviour(new transport(this));
            sb.addSubBehaviour(new executeRASkill(this));
            sb.addSubBehaviour(new finishing_step(this));
        }
        this.addBehaviour(sb);
    }

    @Override
    protected void takeDown()  {
        super.takeDown(); // To change body of generated methods, choose Tools | Templates.
    }

    private ArrayList<String> getExecutionList(String productType) {
        return switch (productType) {
            case "A" -> Utilities.Constants.PROD_A;
            case "B" -> Utilities.Constants.PROD_B;
            case "C" -> Utilities.Constants.PROD_C;
            default -> null;
        };
    }


    /*****************************************************
    * Resource agent functions are found in this section *
    ******************************************************/

    /**
     * behaviour - searchResourceAgentInDF:
     * - Encarregado de procurar os recursos no DF. Apenas é executado uma vez (OneShotBehaviour) methods:
     * - public searchResourceInDF -- constructor
     * - public void action  -------- ???
     */

    private class searchResourceAgentInDF extends SimpleBehaviour {

        public searchResourceAgentInDF(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            DFAgentDescription[] agents_list = null; //Lista de agentes

            try {
                System.out.println("Looking for available agents...");
                agents_list = DFInteraction.SearchInDFByName(executionPlan.get(plan_step), myAgent);
            } catch (FIPAException e) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, e);
            }

            if (agents_list != null) {
                ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                for (int i = 0; i < agents_list.length; i++) {
                    msg.addReceiver(agents_list[i].getName());
                    System.out.println("Msg sent to: " + agents_list[i].getName().getLocalName());
                }
                myAgent.addBehaviour(new contractNetInitiatorRA(myAgent, msg));
            } else {
                System.out.println("RIP - Resource not found: " + executionPlan.get(plan_step));
            }

        }

        @Override
        public boolean done() {
            return true;
        }
    }

    /**
     * behaviour - contractNetInitiatorRA ContractNetInitiator:
     * - Encarregado de começar as negociações com os RA
     * - public initiator -- constructor
     * - protected handleInform -- This method is called every time a inform message is received
     * - protected handleAllResponses -- This method is called when all the responses have been collected or when the timeout is expired, meaning,
     * it collects all agents with a proposal an accept the best one.
     */

    private class contractNetInitiatorRA extends ContractNetInitiator {

        private final ACLMessage msg;

        public contractNetInitiatorRA(Agent a, ACLMessage msg) {
            super(a, msg);
            this.msg = msg;
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": INFORM message received. Next Location: " + inform.getContent());
            next_pos = inform.getContent();
            //request_agv = !current_pos.equals(next_pos);
            product_in_place = !current_pos.equals(next_pos);
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            System.out.println(myAgent.getLocalName() + ": All PROPOSALS received");

            //Evaluate proposals
            int bestProposal = -1;
            AID bestProposer = null;
            ACLMessage accept = null;
            Enumeration e = responses.elements();

            while (e.hasMoreElements()) {

                ACLMessage msg = (ACLMessage) e.nextElement();

                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    acceptances.addElement(reply);

                    int proposal = Integer.parseInt(msg.getContent()); // to define in RA (can be a random)
                    if (proposal > bestProposal) {
                        bestProposal = proposal;
                        bestProposer = msg.getSender();
                        accept = reply;
                    }
                } else {
                    System.out.println("(CFP) REFUSE received from: " + msg.getSender().getLocalName());
                }
                //Accept best proposal
                if (accept != null) {
                    System.out.println("Accepting proposal " + bestProposal + " from responder " + bestProposer.getLocalName());
                    accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

                    bestResource = bestProposer;
                }
                else {
                    try {
                        Thread.sleep(5000);
                        myAgent.addBehaviour(new contractNetInitiatorRA(myAgent, this.msg));
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }

            }
        }
    }

    /**
     * behaviour - execute_skill
     */
    private class executeRASkill extends SimpleBehaviour {

        public executeRASkill(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            System.out.println("Executing RA skill!!!");
            if (product_in_place) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(bestResource);
                msg.setContent(executionPlan.get(plan_step));
                msg.setOntology(ONTOLOGY_EXECUTE_SKILL);
                myAgent.addBehaviour(new fipaResponderRA(myAgent, msg));
                //transport_done = false;
            }
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    /**
     * behaviour - fipaResponderRA
     * - Inicia as comunicações entre os ra agentes (FIPA request initiator ta)
     */
    private class fipaResponderRA extends AchieveREInitiator {

        public fipaResponderRA(Agent a, ACLMessage request) {
            super(a, request);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("Agent " + myAgent.getLocalName() + ": AGREE msg received from " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": Inform message received from " + inform.getSender().getLocalName());

            if (inform.getContent().equalsIgnoreCase("QualityFail")) {
                quality_check = false;
            }
            skill_done = true;
        }
    }

    /*******************************************************
     * Transport agent functions are found in this section *
     *******************************************************/

    /**
     * behaviour - transport
     * - initialize the request of the transport
     */
    private class transport extends SimpleBehaviour {

        private transport(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (!product_in_place) {
                myAgent.addBehaviour(new searchTransportAgentInDF(myAgent));
                //request_agv = false;
                product_in_place = true;
            } /*else {
                transport_done = true;
            }*/
        }

        @Override
        public boolean done() {
            return false;
        }

    }

    /**
     * behaviour - searchTransportAgentInDF
     * - search for an transport agent in the DF and start a communication with it
     */
    private class searchTransportAgentInDF extends OneShotBehaviour {

        public searchTransportAgentInDF(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            DFAgentDescription[] available_agents = null;

            try {
                System.out.println("Looking for an Uber...");
                available_agents = DFInteraction.SearchInDFByName(SK_MOVE, myAgent); //Searching for agents by name "sk_move", meaning, agents capable of transporting
            } catch (FIPAException e) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, e);
            }

            if (available_agents != null) {
                System.out.println("TA Agent " + myAgent.getLocalName() + " was found :" + available_agents.length);

                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST); // Creating a request message

                msg.addReceiver(available_agents[0].getName()); // Add the receiver to the request
                // We send the current, a Token and the next position
                // The TOKEN allows us to send more than one parameter through the request
                msg.setContent(current_pos + Constants.TOKEN + next_pos);
                msg.setOntology(Constants.ONTOLOGY_MOVE); //Set the ontology of the msg

                System.out.println(myAgent.getLocalName() + ": requested " + available_agents[0].getName().getLocalName());

                myAgent.addBehaviour(new fipaResponderTA(myAgent, msg)); //start communication with agent
            } else {
                System.out.println(myAgent.getLocalName() + " couldn't find an Uber (TA)");
            }
        }

    }

    /**
     * behaviour - fipaResponderTA
     * - Inicia as comunicação entre os ta agentes (FIPA request initiator ta)
     * - protected void handleAgree
     * - protected handleInform
     * - protected handleRefuse
     */
    private class fipaResponderTA extends AchieveREInitiator {

        public fipaResponderTA(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println(myAgent.getLocalName() + ": AGREE message received.");
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": INFORM message received.");
            current_pos = next_pos;
            System.out.println(current_pos);
            transport_done = true;
        }

        @Override
        protected void handleRefuse(ACLMessage refuse) {

        }
    }


    /**
     * behaviour - finishing_step
     */
    private class finishing_step extends SimpleBehaviour {

        private boolean finished = false;

        public finishing_step(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            System.out.println("No finishing_step!!!");
            if (skill_done) {
                System.out.println(myAgent.getLocalName() + " finished execution number " + plan_step + ": " + executionPlan.get(plan_step));
                if (executionPlan.get(plan_step).equals("sk_drop")) { //
                    System.out.println("The manufacture of " + myAgent.getLocalName() + " has been completed with SUCCESS!");
                }
                skill_done = false;
                plan_step++;

                if (!quality_check && !recovery_tried && plan_step == 4) {  //caso não estejamos
                    System.out.println("Recovery Initiated");
                    quality_check = true;
                    recovery_tried = true;
                    plan_step = 1;

                    SequentialBehaviour sb2 = new SequentialBehaviour();
                    for (int i = 1; i < executionPlan.size() - 1; i++) {
                        sb2.addSubBehaviour(new searchResourceAgentInDF(this.myAgent));
                        sb2.addSubBehaviour(new transport(this.getAgent()));
                        sb2.addSubBehaviour(new executeRASkill(this.getAgent()));
                        sb2.addSubBehaviour(new finishing_step(this.getAgent()));
                    }
                    addBehaviour(sb2);
                } else if (plan_step == 4) {
                    SequentialBehaviour sb3 = new SequentialBehaviour();
                    sb3.addSubBehaviour(new searchResourceAgentInDF(this.getAgent()));
                    sb3.addSubBehaviour(new transport(this.getAgent()));
                    sb3.addSubBehaviour(new executeRASkill(this.getAgent()));
                    sb3.addSubBehaviour(new finishing_step(this.getAgent()));
                    addBehaviour(sb3);
                }

                this.finished = true;

            }
        }

        @Override
        public boolean done() {
            return this.finished;
        }
    }
}
