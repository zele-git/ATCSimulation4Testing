import com.sun.org.apache.xml.internal.resolver.readers.ExtendedXMLCatalogReader;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;
import java.util.stream.Collectors;

public class STCA_Agent extends Agent {
    private AID[] aircrafts;
    private String aircraftname = null;

    private String serviceType = null;
    //    Simulation.MsgContainer container = new Simulation.MsgContainer();
    private HashMap<String, String> rqstq = new HashMap<String, String>();

    private String nm;

    protected void setup() {
        nm = getAID().getName();
        nm = nm.substring(0, nm.indexOf('@'));
        System.out.println(nm + " is ready.\n");
        // register services
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription service = new ServiceDescription();
        // we can list multiple services
        serviceType = "safety";

        System.out.println(nm + " starting. \n");
        service.setName("safety_check");
        service.setType("safety");
        dfd.addServices(service);
        System.out.println("STCA: service type: " + serviceType + "\n");
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                myAgent.addBehaviour(new CheckSafety());

            }
        });

    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(nm + " terminating. \n");
    }

    public class CheckSafety extends Behaviour {
        private HashMap<String, String> rqstq = new HashMap<String, String>();
        private int phase = 0;

        public void action() {
            List<String> inform_content = new ArrayList<>();
            List<String> rqst_check = new ArrayList<>();

            List<String> confirmlist = new ArrayList<>();
            String fd_code = null;
            switch (phase) {
                case 0:
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                    ACLMessage msg = myAgent.receive(mt);
                    ACLMessage reply = null;

                    if (msg != null) {
                        inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]|\\{|\\}", "").split(",")));
                        rqst_check = new ArrayList<String>(inform_content.subList(2, inform_content.size()));
                        System.out.println(inform_content + "\n STCA: SDP message to STCA");
                        System.out.println(rqst_check + "\n STCA: SDP message to STCA");
                        for (String i : rqst_check) {
                            System.out.println(i.trim().substring(i.indexOf('=')));
                        }
                        // string input should be converted to map
                        for (String item : rqst_check) {
                            if (item != null) {
                                String mapk = null, mapv = null;
                                mapk = item.trim().substring(0, item.indexOf('=') - 1);
                                mapv = item.trim().substring(item.indexOf('='));
                                rqstq.put(mapk, mapv);
                            }
                        }

                        System.out.println(rqstq + "\n STCA: SDP message converted to map ");

                        if (inform_content.get(1).trim().equals("UP")) {
                            //check if there are multiple CONFIRM, true randomly select one aircraft and notify GREEN
                            phase = 1;

                        } else {
                            System.out.println("-------------- STCA:FLAG not raised. \n");
                            block();
                        }
                    } else {
                        System.out.println("-------------- STCA: STCA not yet received safety check request. \n");
//                        phase = 0;
                        block();
                    }
                    break;
                case 1:

                    System.out.println(nm + " searching for aircrafts.\n");
                    DFAgentDescription template1 = new DFAgentDescription();
                    ServiceDescription sd1 = new ServiceDescription();
                    sd1.setName("landing_aircraft");
                    sd1.setType("landing");
                    template1.addServices(sd1);
                    String agent_name = null;
                    try {
                        // search for airports
                        DFAgentDescription[] result = DFService.search(myAgent, template1);
                        aircrafts = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            aircrafts[i] = result[i].getName();
                            agent_name = result[i].getName().toString();
                        }
                        if (aircrafts != null) {
                            System.out.println(nm + " found the following Aircrafts: " + aircrafts + "\n");
                            phase = 2;
                        } else {
                            System.out.println(nm + " no aircraft nearby.\n");
                            phase = 0;
                            break;
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    break;
                case 2:
                    // inform to aircraft
                    for (HashMap.Entry<String, String> entry : rqstq.entrySet()) {
                        if (entry.getValue().trim().equals("CONFIRMED")) {
                            confirmlist.add(entry.getKey());
                        }
                    }
                    System.out.println(confirmlist + "\n STCA: confirmed list. \n");
                    if (confirmlist.size() > 0) {
                        //random select one and assign GREEN
                        Random rand = new Random();
                        fd_code = confirmlist.get(rand.nextInt(confirmlist.size()));
                    }
                    System.out.println(fd_code + "\n STCA: selected from the confirmed list. \n");

                    List<String> cfp_msg = new ArrayList<>();
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    try {
                        for (int i = 0; i < aircrafts.length; ++i) {
                            cfp.addReceiver(aircrafts[i]);
                            System.out.println(aircrafts[i].getName() + "\n");
                        }
                        cfp_msg.add(fd_code);
                        cfp_msg.add("GREEN");
                        cfp.setContent(cfp_msg.toString());
                        cfp.setConversationId("landing_aircraft");
                        cfp.setReplyWith("cfp " + System.currentTimeMillis());
                        myAgent.send(cfp);
                        System.out.println(nm + " submitted CFP to FD:" + aircrafts + " .\n");
                        phase = 3;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if (phase != 3) {
                        System.out.println(nm + " NOT submitted CFP to STCA. \n");
                        phase = 0;
                    }
                    break;
            }

        }

        public boolean done() {
            return (phase == 3);
        }
    }
}
