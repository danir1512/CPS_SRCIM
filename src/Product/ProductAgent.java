package Product;

import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import jade.proto.ContractNetResponder;
import jade.util.Logger;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;

/**
 *
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
    boolean request_agv, negotation_ra_done;
    public boolean search_InDF_done = false;

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
        this.negotation_ra_done = false;

        //SequentialBehaviour encadeia vários sub-behaviours que são executados de forma sucessiva
        SequentialBehaviour sb = new SequentialBehaviour();

        // 1 - Procurar Recursos no DF

        for(int i=0; i < executionPlan.size()-1; i++){
            sb.addSubBehaviour(new searchResourceInDF(this));
            //sb.addSubBehaviour(new transport(this));
        }
        this.addBehaviour(sb);
    }

    @Override
    protected void takeDown() {
        super.takeDown(); //To change body of generated methods, choose Tools | Templates.
    }
    
    private ArrayList<String> getExecutionList(String productType){
        switch(productType){
            case "A": return Utilities.Constants.PROD_A;
            case "B": return Utilities.Constants.PROD_B;
            case "C": return Utilities.Constants.PROD_C;
        }
        return null;
    }

    /**
        behaviour - dealer:
            - Encarregado de iniciar as negociações com os outros agentes:

            - public dealer -- constructor
            - public void handleInform  -------- ???
     */

    private class dealer extends ContractNetInitiator {

        private ACLMessage msg;

        public dealer(Agent a, ACLMessage msg){
            super(a, msg);
            this.msg = msg;
        }

        @Override
        protected void handleInform(ACLMessage inform){
            System.out.println(myAgent.getLocalName() + ": INFORM message received");

            next_pos = inform.getContent();

            //Trata da situação em que já estamos na situação pretendida
            if(current_pos.equals(next_pos)){
                request_agv = false;
            } else {
                request_agv = true;
            }

            negotation_ra_done = true;
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances){ //when all the proposals have been analyse, this
            System.out.println(myAgent.getLocalName() + ": ALL PROPOSALS received");

            AID best_agent = null;
            int best_bet = -1;
            ACLMessage accept = null;
            Enumeration e = responses.elements();

            while(e.hasMoreElements()){

                if(msg.getPerformative() == ACLMessage.PROPOSE){ //if th msg receive is a proposal
                    ACLMessage reply = msg.createReply();       //create reply for the proposal
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL); //set the default performative (type of communication act, e.x. reject or accept proposal, inform, request)
                    acceptances.addElement(reply);

                    int proposal = Integer.parseInt(msg.getContent());
                    if(proposal > best_bet){
                        best_bet = proposal;
                        best_agent = msg.getSender();
                        accept = reply;
                    }
                }
                else{
                    System.out.println("(CFP) REFUSE received from: " + msg.getSender().getLocalName());
                }
            }

            /**
             * Basicmaente estamos a guardar a melhor resposta no "accept" e depois estamos a mudar a performative para ACCEPT_PROPOSAL no if de embaixo,
             * mas acho que podemos fazer isso logo acima
             *
             * **/
            //Accept best proposal
            if(accept != null){
                System.out.println("Porposal" + best_bet + "accepted from agent" + best_agent.getLocalName());
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

                bestResource = best_agent;
            }

            myAgent.addBehaviour(new dealer(myAgent,this.msg));
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
        public searchResourceInDF(Agent a){
            super(a);
        }

        @Override
        public void action() {

            DFAgentDescription[] agents_list = null; //Lista de agentes

            try {
                System.out.println("Looking for available agents...");
                agents_list = DFInteraction.SearchInDFByName(executionPlan.get(plan_step),myAgent);
            } catch (FIPAException e) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE,null,e);
            }

            if (agents_list != null)
            {
                ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                for(int i=0; i < agents_list.length; i++){
                    msg.addReceiver(agents_list[i].getName());
                    System.out.println("Msg  sent to:" + agents_list[i].getName().getLocalName());
                }

                myAgent.addBehaviour(new dealer(myAgent,msg));

            } else{

                System.out.println("RIP");
            }



        }
    }
    
}
