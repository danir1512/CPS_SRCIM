package Product;

import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
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
    boolean request_agv, ra_negotiation_done, transport_done;

    @Override
    protected void setup() {
        Object[] args = this.getArguments();
        this.id = (String) args[0];
        this.executionPlan = this.getExecutionList((String) args[1]); //get the execution plan given the input in the UI
        System.out.println("Product launched: " + this.id + " Requires: " + executionPlan);

        // TO DO: Add necessary behaviour/s for the product to control the flow
        // of its own production

        this.plan_step = 0;
        this.current_pos = "source";

        this.quality_check = true;
        this.recovery_tried = false;
        this.request_agv = false;

        //SequentialBehaviour encadeia vários sub-behaviours que são executados de forma sucessiva
        SequentialBehaviour sb = new SequentialBehaviour();

        // 1 - Procurar Recursos no DF

        for (int i = 0; i < executionPlan.size() - 1; i++) {
            sb.addSubBehaviour(new searchResourceInDF(this));
            //sb.addSubBehaviour(new transport(this));
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
     behaviour - initiator ContractNetInitiator:
     - Encarregado de começar as negociações com os RA
     - public initiator -- constructor
     - protected handleInform -- This method is called every time a inform message is received
     - protected handleAllResponses -- This method is called when all the responses have been collected or when the timeout is expired, meaning,
     it collects all agents with a proposal an accept the best one.
     */

    private class initiator extends ContractNetInitiator {

        private final ACLMessage msg;

        public initiator(Agent a, ACLMessage msg) {

            super(a, msg);
            this.msg = msg;
        }

        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println(myAgent.getLocalName() + ": INFORM message received. Next Location: "+ inform.getContent());
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

            while(e.hasMoreElements()){

                ACLMessage msg = (ACLMessage)e.nextElement();

                if(msg.getPerformative() == ACLMessage.PROPOSE){
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    acceptances.addElement(reply);

                    int proposal = Integer.parseInt(msg.getContent()); // to define in RA (can be a random)
                    if(proposal > bestProposal){
                        bestProposal = proposal;
                        bestProposer = msg.getSender();
                        accept = reply;
                    }
                }
                else{
                    System.out.println("(CFP) REFUSE received from: " + msg.getSender().getLocalName());
                }
                //Acept best proposal
                if(accept != null){
                    System.out.println("Accepting proposal " + bestProposal + " from responder " + bestProposer.getLocalName());
                    accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

                    bestResource = bestProposer;
                }

                myAgent.addBehaviour(new initiator(myAgent,this.msg));
            }
        }
    }

    /**
     behaviour - searchResourceInDF:
     - Encarregado de procurar os recursos no DF. Apenas é executado uma vez (OneShotBehaviour)
     methods:
     - public searchResourceInDF -- constructor
     - public void action  -------- ???
     */

    private class searchResourceInDF extends OneShotBehaviour {

        //Construtor
        public searchResourceInDF(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            DFAgentDescription[] agents_list = null; //Lista de agentes
            //O henriques é burro
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
                myAgent.addBehaviour(new initiator(myAgent,msg));
            } else {
                System.out.println("RIP - Resource not found: " + executionPlan.get(plan_step));
            }


        }
    }

    /**
     *
     * behaviour - comStart
     * - Inicia as comunicação entre os agentes
     * - protected void handleAgree
     * - protected handleInform
     * - protected handleRefuse
     *
     */

    private class comStart extends AchieveREInitiator {

        private final ACLMessage msg;

        public comStart(Agent a, ACLMessage msg) {
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

        }
    }
}
