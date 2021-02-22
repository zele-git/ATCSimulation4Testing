import com.sun.org.apache.xerces.internal.impl.dv.xs.AbstractDateTimeDV;
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

    private HashMap<String, String> rqstq = new HashMap<String, String>();
    Simulation.AircraftNumber aircraftNumber = new Simulation.AircraftNumber();
    private String nm;
    private AID sniff_aid;

    protected void setup() {
        nm = getAID().getName();
        nm = nm.substring(0, nm.indexOf('@'));
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription service = new ServiceDescription();


        System.out.println(nm + " starting. \n");
        service.setName("stca");
        service.setType("safety");
        dfd.addServices(service);
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
        System.out.println(nm + ": terminating. \n");
    }

    public class CheckSafety extends Behaviour {
        private HashMap<String, String> rqstq = new HashMap<String, String>();
        private int phase = 0;
        private int count = 0;

        public void action() {
            List<String> inform_content = new ArrayList<>();
            List<String> rqst_check = new ArrayList<>();
            List<String> aid_list = new ArrayList<>();

            List<String> confirmlist = new ArrayList<>();
            String fd_code = null;

            switch (phase) {
                case 0:// GET message from SDP
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    ACLMessage msg = myAgent.receive(mt);

                    if (msg != null) {
                        inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]|\\{|\\}", "").split(",")));
                        rqst_check = new ArrayList<String>(inform_content.subList(2, inform_content.size()));
                       // System.out.println(inform_content + "\n STCA: SDP message to STCA");

                        if (rqst_check.size()> 0) {//convert list to map
                           // System.out.println(nm + " request check is not empty. \n ");
                            for (String item : rqst_check) {
                                if (item != null) {
                                    String mapk = null, mapv = null;
                                    try {
                                        mapk = item.trim().substring(0, item.indexOf('=') - 1);
                                        mapv = item.trim().substring(item.indexOf('='));
                                        rqstq.put(mapk, mapv);
                                    } catch (Exception fe) {
                                        fe.printStackTrace();
                                    }
                                }
                            }
                        }
                        //System.out.println(rqstq + "\n STCA: SDP message converted to map ");
                        if (inform_content.get(1).trim().equals("UP")) {
                            //check if there are multiple CONFIRM, true randomly select one aircraft and notify GREEN
                            phase = 1;
                        } else {
                            System.out.println("-------------- STCA:FLAG not raised. \n");
//                            phase = 0;
                            block();
                        }

                    } else {
                        //System.out.println(nm + " Not yet received request. \n");
                        block();
//                        phase = 0;
                    }

                case 1://locate aircraft
                    System.out.println(nm + " searching for Aircraft.\n");
                    DFAgentDescription tmplt = new DFAgentDescription();
                    ServiceDescription sd1 = new ServiceDescription();
                    sd1.setName("aircraft");
                    sd1.setType("landing");
                    tmplt.addServices(sd1);
                    List<String> aircraft_name = new ArrayList<>();

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, tmplt);
                        aircrafts = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            aircrafts[i] = result[i].getName();
                            aircraft_name.add(result[i].getName().toString());
                        }
                        if (aircraft_name.size()> 0) {
                            //System.out.println(nm + ": found the following Aircraft: " + aircraft_name + "\n");
                            // extracting aircraft name from the result
                            for (int i = 0; i < result.length; ++i) {
                                aircrafts[i] = result[i].getName();
                                String aid = result[i].getName().getLocalName();//.toString().trim().substring(result[i].getName().toString().trim().indexOf('F'), result[i].getName().toString().trim().indexOf('@'));

                               // String aid = result[i].getName().toString().trim().substring(result[i].getName().toString().trim().indexOf('F'), result[i].getName().toString().trim().indexOf('@'));
                                aid_list.add(aid);// if aircraft is not in this list, STCA should not send GREEN
                            }
                            // select aircrafts to be INFORMED
                            for (HashMap.Entry<String, String> entry : rqstq.entrySet()) {
                                if (entry.getValue().trim().equals("CONFIRMED")) {
                                    confirmlist.add(entry.getKey());
                                }
                            }

                            //System.out.println(confirmlist + "\n STCA: confirmed list. \n");
                            if (confirmlist.size() > 0) {
                                //random select one and assign GREEN
                                Random rand = new Random();
                                fd_code = confirmlist.get(rand.nextInt(confirmlist.size()));
                               // System.out.println(fd_code + "\n STCA: selected from the confirmed list. \n");
                                //System.out.println(aid_list + "\n STCA: AID list. \n");

                                List<String> inform_msg = new ArrayList<>();
                                ACLMessage inform = new ACLMessage(ACLMessage.INFORM); // broadcast to all FDs
                                if (fd_code != null) {
                                    //check if still selected one is alive, else select other one
                                    if (aid_list.contains(fd_code)) {
                                        try {

                                            inform_msg.add(fd_code);
                                            inform_msg.add("GREEN");
                                            inform.setContent(inform_msg.toString());
                                            inform.setConversationId("aircraft");
                                            for (int i = 0; i < aircrafts.length; ++i) {
                                                inform.addReceiver(aircrafts[i]);// for all existing aircraft
                                                System.out.println(nm + " ==> INFORM " + fd_code + " (GREEN) ==> " + aircrafts[i].getName() + "\n");
                                            }
                                            myAgent.send(inform);
                                            if (!fd_code.equals("FD0")){//sniffer code

                                                DFAgentDescription template = new DFAgentDescription();
                                                ServiceDescription sd = new ServiceDescription();
                                                sd.setName("tester");
                                                sd.setType("landing");
                                                template.addServices(sd);
                                                try {

                                                    DFAgentDescription[] resulttester = DFService.search(myAgent, template);
                                                    AID[] tester = new AID[resulttester.length];
                                                    for (int i = 0; i < resulttester.length; ++i) {
                                                        tester[i] = resulttester[i].getName();
                                                        sniff_aid = resulttester[i].getName();
                                                    }

                                                } catch (FIPAException fe) {
                                                    fe.printStackTrace();
                                                }
//                                                System.out.println(nm + " ==> INFORM " + sniff_aid + " (LEAKED) ==> \n");
                                                inform.setConversationId("sniffer");
                                                inform.addReceiver(sniff_aid);
                                                myAgent.send(inform);
                                            }
                                            phase = 2;
                                            //block();
                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }
                                    } else {
                                        confirmlist.remove(fd_code);
                                        //System.out.println(fd_code + "\n STCA: delete NOTALIVE aircraft. \n");
                                        phase = 1;
//                                        block();
                                    }
                                } else {
                                    //System.out.println(fd_code + "\n STCA: selected FD is not alive. \n");
                                    phase = 1;
//                                    block();
                                }
                            } else {
                                //System.out.println(nm + ": confirmation list empty. \n");
//                                phase = 0;
                                block();
                            }

                        } else {
                            //System.out.println(nm + " no Aircraft nearby.\n");
//                            phase = 1;
                            block();
                        }

                    } catch (Exception fe) {
                        fe.printStackTrace();
                    }

                case 2:// get message from FD
                    mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    msg = myAgent.receive(mt);
                    if (msg != null) {
//                        if (msg.getSender().getName() != "Manager") {
                        inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                        if (inform_content.get(1).trim().equals("RELEASED")) {
                            count++; //controlling whether all airplane have accomplished their goal
                            //System.out.println(nm + " \n counting alive FD\n");
                            if (count == aircraftNumber.getAN()) { //number of aircraft reported RELEASED
                                myAgent.doDelete();
                            }
                            //phase = 1;
                        }else{
                           // System.out.println(nm + " \n Aircraft replay is NOT a RELEASED\n");
//                            phase = 0;
                            block();
                        }
                    } else {
                        //System.out.println(nm + " \n Aircraft replay not yet received.\n");
//                        phase = 0;
                        block();

                    }
                    phase = 3;
            }
        }

        public boolean done() {
            return (phase == 3);
        }
    }
}
