package Product;

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
    boolean agent_negotiation, product_in_place, skill_done;
    AID bestResource; //AID -> agent identifier

    /*
    * plan_step -> used to keep track of the plan step;
    * current_pos -> has the actual product position;
    * next_pos -> has the next position to which the product has to go;
    * skill_done -> used to verify if each skill has been executed;
    * product_in_place -> used to determine if the product is in the desired place;
    * */
    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.executionPlan = this.getExecutionList((String) args[1]); //get the execution plan given the input in the UI
        System.out.println("Product launched: " + this.id + " Requires: " + executionPlan);

        this.plan_step = 0;
        this.current_pos = "Source";
        this.agent_negotiation = false;
        this.product_in_place = false;
        this.skill_done = false;

        // TO DO: Add necessary behaviour/s for the product to control the flow
        // of its own production
        SequentialBehaviour sb = new SequentialBehaviour(); // SequentialBehaviour encadeia vários sub-behaviours que são executados de forma sucessiva
        for (int i = 0; i < executionPlan.size(); i++) {
            sb.addSubBehaviour(new searchResourceAgentInDF(this));
            sb.addSubBehaviour(new transport(this));
            sb.addSubBehaviour(new executeRASkill(this));
            //sb.addSubBehaviour(new finishing_step(this));
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

        private boolean negotiating = false;

        public searchResourceAgentInDF(Agent a) {
            super(a);
        }

        @Override
        public void action() {

            if(!negotiating) {
                skill_done = false; // Reset this variable
                negotiating = true;
                DFAgentDescription[] agents_list = null;
                try {
                    System.out.println("Looking for available agents...");
                    agents_list = DFInteraction.SearchInDFByName(executionPlan.get(plan_step), myAgent);
                    System.out.println(agents_list[0].getName().getLocalName() + plan_step);
                } catch (FIPAException e) {
                    Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, e);
                }

                if (agents_list != null) {
                    ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                    for (DFAgentDescription dfAgentDescription : agents_list) {
                        msg.addReceiver(dfAgentDescription.getName());
                        System.out.println("Msg sent to: " + dfAgentDescription.getName().getLocalName());
                    }
                    myAgent.addBehaviour(new contractNetInitiatorRA(myAgent, msg));
                } else {
                    System.out.println("RIP - Resource not found: " + executionPlan.get(plan_step));
                }
            }
        }

        @Override
        public boolean done() {
            return agent_negotiation;
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
            //if(plan_step != 0)
            product_in_place = current_pos.equalsIgnoreCase(next_pos);
            agent_negotiation = true;
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

            }

            //Accept best proposal
            if (accept != null) {
                System.out.println(myAgent.getLocalName() + ": Accepting proposal " + bestProposal + " from responder " + bestProposer.getLocalName());
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

    /**
     * behaviour - execute_skill
     */
    private class executeRASkill extends SimpleBehaviour {

        private boolean executing_skill = false;

        public executeRASkill(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if(!executing_skill){
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(bestResource);
                msg.setContent(executionPlan.get(plan_step));
                msg.setOntology(ONTOLOGY_EXECUTE_SKILL);
                myAgent.addBehaviour(new fipaResponderRA(myAgent, msg));
                executing_skill = true;
                System.out.println(myAgent.getLocalName() + ": " + bestResource.getLocalName() + " executing skill.");
            }
        }

        @Override
        public boolean done() {
            return skill_done;
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
            System.out.println(myAgent.getLocalName() + ": AGREE msg received from " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": Inform message received from " + inform.getSender().getLocalName());
            skill_done = true;
            plan_step++;
            agent_negotiation = false; // To reset this variable
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

        private boolean agv_requested = false;

        private transport(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (!product_in_place && !agv_requested) {
                myAgent.addBehaviour(new searchTransportAgentInDF(myAgent));
                agv_requested = true;
            }
        }

        @Override
        public boolean done() {
            return product_in_place;
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
                System.out.println("Transport Agent for " + myAgent.getLocalName() + " was found :" + available_agents.length);
                // Creating a request message
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                // Add the receiver to the request
                msg.addReceiver(available_agents[0].getName());
                // We send the current, a Token and the next position
                // The TOKEN allows us to send more than one parameter through the request
                msg.setContent(current_pos + Constants.TOKEN + next_pos);
                // Set the ontology of the msg
                msg.setOntology(Constants.ONTOLOGY_MOVE);
                System.out.println(myAgent.getLocalName() + ": Requested " + available_agents[0].getName().getLocalName());

                myAgent.addBehaviour(new fipaResponderTA(myAgent, msg)); //start communication with agent
            } else {
                System.out.println(myAgent.getLocalName() + " couldn't find an Uber (TA)");
            }
        }

    }

    /**
     * behaviour - fipaResponderTA
     * - Inicia as comunicação entre os transport agents (FIPA request initiator ta)
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
            System.out.println(myAgent.getLocalName() + ": AGV AGREE message received.");
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": AGV INFORM message received." + inform.getContent());
            current_pos = next_pos;
            product_in_place = true;
        }

        @Override
        protected void handleRefuse(ACLMessage refuse) {

        }
    }
}
