import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Test_Agent extends Agent {
    //based on test case information, it checks if a precondition is satisfied and input test data to the system.
    private AID uniq_aid;
    private String nm;

    protected void setup() {
        nm = getAID().getName();
        nm = nm.substring(0, nm.indexOf('@'));
        DFAgentDescription dfd = new DFAgentDescription();

        ServiceDescription service = new ServiceDescription();

        System.out.println(nm + " starting. \n");
        service.setName("tester");
        service.setType("landing");
        dfd.addServices(service);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                myAgent.addBehaviour(new SniffOperation());

            }
        });


    }

    protected void takeDown() {
        System.out.println(nm + " terminating. \n");
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private class SniffOperation extends CyclicBehaviour {
        private MessageTemplate mt;

        public void action() {

            try {
                mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                ACLMessage stca_msg = myAgent.blockingReceive(mt);

                List<String> inform_msg = new ArrayList<>();
                ACLMessage inform = new ACLMessage(ACLMessage.INFORM); // broadcast to all FDs
                if (stca_msg != null) {
                    System.out.println(nm + ": precondition satisfied .\n");
                    List<AID> alive_aircraft = new ArrayList<>();
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setName("aircraft");
                    sd.setType("landing");
                    template.addServices(sd);
                    try {

                        DFAgentDescription[] resulttester = DFService.search(myAgent, template);
                        for (int i = 0; i < resulttester.length; ++i) {
                            alive_aircraft.add(resulttester[i].getName());
//                            if (resulttester[i].getName().getLocalName().equals("FD0"))
//                                uniq_aid = resulttester[i].getName();
                        }

                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    Random rand = new Random();
                    uniq_aid = alive_aircraft.get(rand.nextInt(alive_aircraft.size()));

                    inform_msg.add(uniq_aid.getLocalName());
                    inform_msg.add("GREEN");
                    inform.setContent(inform_msg.toString());
                    inform.setConversationId("aircraft");
                    inform.addReceiver(uniq_aid);// for all existing aircraft
                    System.out.println(nm + " ==> INFORM " + uniq_aid.getLocalName() + " (LEAKED) \n");
                    myAgent.send(inform);

                } else {
                    System.out.println(nm + ": precondition NOT satisfied .\n");
                }

            } catch (Exception fe) {
                fe.printStackTrace();
            }


        }


    }
}
