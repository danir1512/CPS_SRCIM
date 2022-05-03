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
    AID bestResource, agv, ta; //AID -> agent identifier
    boolean request_agv, ra_negotiation_done, transport_done, skill_done;

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
        this.current_pos = "source";
        this.quality_check = true;
        this.recovery_tried = false;
        this.request_agv = false;
        this.skill_done = false;

        // TO DO: Add necessary behaviour/s for the product to control the flow
        // of its own production
        SequentialBehaviour sb = new SequentialBehaviour(); // SequentialBehaviour encadeia vários sub-behaviours que são executados de forma sucessiva
        for (int i = 0; i < executionPlan.size() - 1; i++) {
            sb.addSubBehaviour(new searchResourceInDF(this));
            sb.addSubBehaviour(new transport(this));
            sb.addSubBehaviour(new execute_skill(this));
            sb.addSubBehaviour(new finishing_step(this));
        }
        this.addBehaviour(sb);
    }

    @Override
    protected void takeDown() {
        super.takeDown(); //To change body of generated methods, choose Tools | Templates.
    }

    private ArrayList<String> getExecutionList(String productType) {
        return switch (productType) {
            case "A" -> Utilities.Constants.PROD_A;
            case "B" -> Utilities.Constants.PROD_B;
            case "C" -> Utilities.Constants.PROD_C;
            default -> null;
        };
    }

    /**
     * behaviour - initiator ContractNetInitiator:
     * - Encarregado de começar as negociações com os RA
     * - public initiator -- constructor
     * - protected handleInform -- This method is called every time a inform message is received
     * - protected handleAllResponses -- This method is called when all the responses have been collected or when the timeout is expired, meaning,
     * it collects all agents with a proposal an accept the best one.
     */

    private class initiator extends ContractNetInitiator {

        private final ACLMessage msg;

        public initiator(Agent a, ACLMessage msg) {

            super(a, msg);
            this.msg = msg;
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": INFORM message received. Next Location: " + inform.getContent());
            next_pos = inform.getContent();
            request_agv = !current_pos.equals(next_pos);
            ra_negotiation_done = true;
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
                //Acept best proposal
                if (accept != null) {
                    System.out.println("Accepting proposal " + bestProposal + " from responder " + bestProposer.getLocalName());
                    accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

                    bestResource = bestProposer;
                }

                myAgent.addBehaviour(new initiator(myAgent, this.msg));
            }
        }
    }

    /**
     * behaviour - searchResourceInDF:
     * - Encarregado de procurar os recursos no DF. Apenas é executado uma vez (OneShotBehaviour) methods:
     * - public searchResourceInDF -- constructor
     * - public void action  -------- ???
     */

    private class searchResourceInDF extends OneShotBehaviour {

        //Construtor
        public searchResourceInDF(Agent a) {
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
                    System.out.println("Msg  sent to:" + agents_list[i].getName().getLocalName());
                }
                myAgent.addBehaviour(new initiator(myAgent, msg));
            } else {
                System.out.println("RIP - Resource not found: " + executionPlan.get(plan_step));
            }

        }
    }

    /**
     * behaviour - comStart_ta
     * - Inicia as comunicação entre os ta agentes (FIPA request initiator ta)
     * - protected void handleAgree
     * - protected handleInform
     * - protected handleRefuse
     */

    private class comStart_ta extends AchieveREInitiator {

        private final ACLMessage msg;

        public comStart_ta(Agent a, ACLMessage msg) {
            super(a, msg);
            this.msg = msg;
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            super.handleAgree(agree);
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": INFORM message received");

            current_pos = next_pos;
            transport_done = true;
        }

        @Override
        protected void handleRefuse(ACLMessage refuse) {
            //falta adicionar coisas para quando o transporte é recusado
            //nomeadamente se já estiver ocupado noutro transporte
        }
    }

    /**
     * behaviour - transport
     * - initialize the request of the transport
     */
    private class transport extends SimpleBehaviour {

        private boolean finished;

        private transport(Agent a) {
            super(a);
            finished = false;
        }

        @Override
        public void action() {
            if (ra_negotiation_done) { // once the negotiation is over, search for ta in the df
                if (request_agv) {
                    myAgent.addBehaviour(new search_ta_in_df(myAgent));
                    request_agv = false;
                } else {
                    transport_done = true;
                }

                ra_negotiation_done = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    /**
     * behaviour - search_ta_in_DF
     * - search for an transport agent in the DF and start a communication with it
     */
    private class search_ta_in_df extends OneShotBehaviour {

        public search_ta_in_df(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            DFAgentDescription[] available_agents = null;

            try {
                System.out.println("Looking for an Uber...");
                available_agents = DFInteraction.SearchInDFByName("sk_move", myAgent); //Searching for agents by name "sk_move", meaning, agents capable of transporting
            } catch (FIPAException e) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE, null, e);
            }

            if (available_agents != null) {

                System.out.println("TA Agent " + myAgent.getLocalName() + " was found :" + available_agents.length);

                ACLMessage req = new ACLMessage(ACLMessage.REQUEST); //Creating a request message

                req.setContent(current_pos + Constants.TOKEN + next_pos); // we send the current and next position. The TOKEN allows us to send more than one parameter through  the request
                req.setOntology(Constants.ONTOLOGY_MOVE); //Set the ontology of the req

                ta = available_agents[0].getName(); //we get the first agent
                req.addReceiver(ta); //Add the receiver to the request

                req.getAllReceiver().next().toString();

                System.out.println(myAgent.getLocalName() + ": requested " + available_agents[0].getName().getLocalName());

                myAgent.addBehaviour(new comStart_ta(myAgent, req)); //start communication with agent
            } else {
                System.out.println(myAgent.getLocalName() + "Couldn't find an Uber (TA)");
            }
        }

    }

    /**
     * behaviour - comStart_ra
     * - Inicia as comunicações entre os ra agentes (FIPA request initiator ta)
     */

    private class comStart_ra extends AchieveREInitiator {

        public comStart_ra(Agent a, ACLMessage request) {
            super(a, request);
        }

        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("Agent " + myAgent.getLocalName() + ": AGREE msg received from: " + agree.getSender().getLocalName());
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": Inform message received from: " + inform.getSender().getLocalName());

            if (inform.getContent().equalsIgnoreCase("QualityFail")) {
                quality_check = false;
            }
            skill_done = true;
        }
    }

    /**
     * behaviour - execute_skill
     */
    private class execute_skill extends SimpleBehaviour {

        private boolean finished;

        public execute_skill(Agent a) {
            super(a);
            this.finished = false;
        }

        @Override
        public void action() {
            if (transport_done) {
                ACLMessage req = new ACLMessage(ACLMessage.REQUEST);

                req.setContent(executionPlan.get(plan_step));
                req.addReceiver(bestResource);

                myAgent.addBehaviour(new comStart_ra(myAgent, req));

                transport_done = false;
                this.finished = true;
            }
        }

        @Override
        public boolean done() {
            return this.finished;
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
            if (skill_done) {
                System.out.println(myAgent.getLocalName() + " finished execution number: " + plan_step + " step: " + executionPlan.get(plan_step) + "\n");
                if (executionPlan.get(plan_step).equals("sk_drop")) { //
                    System.out.println("The manufacture of " + myAgent.getLocalName() + " has been completed with SUCCESS!");
                }
                skill_done = false;
                plan_step++;

                if (!quality_check && !recovery_tried && plan_step == 4) {  //caso não estajamos
                    System.out.println("Recovery Initiated");
                    quality_check = true;
                    recovery_tried = true;
                    plan_step = 1;

                    SequentialBehaviour sb2 = new SequentialBehaviour();
                    for (int i = 1; i < executionPlan.size() - 1; i++) {
                        sb2.addSubBehaviour(new searchResourceInDF(this.myAgent));
                        sb2.addSubBehaviour(new transport(this.getAgent()));
                        sb2.addSubBehaviour(new execute_skill(this.getAgent()));
                        sb2.addSubBehaviour(new finishing_step(this.getAgent()));
                    }
                    addBehaviour(sb2);
                } else if (plan_step == 4) {
                    SequentialBehaviour sb3 = new SequentialBehaviour();
                    sb3.addSubBehaviour(new searchResourceInDF(this.getAgent()));
                    sb3.addSubBehaviour(new transport(this.getAgent()));
                    sb3.addSubBehaviour(new execute_skill(this.getAgent()));
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
