package Product;

import Utilities.DFInteraction;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.util.Logger;

import java.util.ArrayList;
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
    boolean request_agv;
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

    /*
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

            DFAgentDescription[] dfd = null; //Lista de agentes

            try {
                System.out.println("Looking for available agents...");
                dfd = DFInteraction.SearchInDFByName(executionPlan.get(plan_step),myAgent);
            } catch (FIPAException e) {
                Logger.getLogger(ProductAgent.class.getName()).log(Level.SEVERE,null,e);
            }

            if (dfd != null)
            {
                System.out.println("Found some agent/s");
                System.out.println("tua mae");
            }



        }
    }
    
}
