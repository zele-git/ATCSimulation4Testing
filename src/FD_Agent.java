import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FD_Agent extends Agent {
    private AID[] airports;
    private AID sdp_aid;
    private AID stca_aid;


    private AID[] safety_agents;
    private List<String> airport_name = new ArrayList<>();
    private String nm;

    protected void setup() {
        nm = getAID().getName();
        nm = nm.substring(0, nm.indexOf('@'));
        DFAgentDescription dfd = new DFAgentDescription();

        ServiceDescription service = new ServiceDescription();

        System.out.println(nm + " starting. \n");
        service.setName("aircraft");
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
                myAgent.addBehaviour(new LandingOperation());

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

    private class LandingOperation extends Behaviour {
        private MessageTemplate mt;
        private int phase = 0;
        List<String> inform_content = null;

        public void action() {
            switch (phase) {
                case 0://search for airport
                    //System.out.println(nm + ": searching for airport.\n");
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setName("airport");
                    sd.setType("manager");
                    template.addServices(sd);
                    try {
                        airport_name.clear();
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        airports = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            airports[i] = result[i].getName();
                            sdp_aid = result[i].getName();
                            airport_name.add(result[i].getName().toString());
                        }
                        if (airport_name != null) {
                            //System.out.println(nm + ": found the following airports: " + airport_name + "\n");
                            phase = 1;
                        } else {
                            //System.out.println(nm + ": no airport nearby.\n");
                            phase = 0;
                            //block();
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                case 1://send INFORM to all all airports or SDP
                      try {
                            List<String> info_content = new ArrayList<>();
                            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);

                            info_content.add(nm);
                            info_content.add("RQST");
                            inform.setContent(info_content.toString());
                            inform.setConversationId("airport");
                            inform.addReceiver(sdp_aid);
                            myAgent.send(inform);
                            System.out.println(nm + " ==> INFORM (RQST) ==> " + sdp_aid.getLocalName() + "\n");

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    phase = 2;
                case 2://receive responses from SDP
                    try {
                        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                        ACLMessage sdp_msg = myAgent.blockingReceive(mt);
                        if (sdp_msg != null) {
                            //System.out.println("\n SDP name is : " + sdp_msg.getSender() + ".\n");
                            //System.out.println(nm + ": SDP replay is not empty. \n");
                            List<String> sdp_inform_content = new ArrayList<String>(Arrays.asList(sdp_msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                            if (nm.equals(sdp_inform_content.get(0))) {
                                //System.out.println(sdp_inform_content + " \n FD received SDP INFORM content \n");
                                if (sdp_inform_content.get(1).trim().equals("CLEARED")) {
                                    List<String> info_content = new ArrayList<>();
                                    ACLMessage inform = new ACLMessage(ACLMessage.INFORM);

                                    info_content.add(nm);
                                    info_content.add("ACK");
                                    inform.setContent(info_content.toString());
                                    inform.setConversationId("airport");
                                    inform.addReceiver(sdp_aid);
                                    myAgent.send(inform);
                                    System.out.println(nm + " ==> INFORM (ACK) ==> " + sdp_aid.getLocalName() + "\n");
                                    System.out.println(nm + ": preparing to land.\n");
                                    phase = 3;
                                }
                            } else {
                               // System.out.println(nm + ": message not addressed to self. \n");
                                //phase = 2;
                                block();
                            }
                        } else {
                            //System.out.println(nm + ": SDP replay is empty. \n");
                            phase = 2;
                            //block();
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                case 3://check for safety, first search for safety agent
                    System.out.println(nm + " searching for Safety Agent.\n");
                    DFAgentDescription template1 = new DFAgentDescription();
                    ServiceDescription sd1 = new ServiceDescription();
                    sd1.setName("stca");
                    sd1.setType("safety");
                    template1.addServices(sd1);
                    List<String> agent_name = new ArrayList<>();
                    try {
                        // search for airports
                        DFAgentDescription[] result = DFService.search(myAgent, template1);
                        safety_agents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            safety_agents[i] = result[i].getName();
                            stca_aid = result[i].getName();
                            agent_name.add(result[i].getName().toString());
                        }
                        if (agent_name != null) {
                            //System.out.println(nm + ": found the following SafetyAgent: " + agent_name + "\n");
                            phase = 4;//
                        } else {
                            //System.out.println(nm + ": no SafetyAgent nearby.\n");
                            phase = 3;
                            //block();
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                case 4:  //GET STCA permission, RELEASE INFORM TO SDP
                    try {
                        List<String> stca_inform_content = new ArrayList<>();
                        mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                        ACLMessage stca_msg = myAgent.blockingReceive(mt);
                        if (stca_msg != null) {
                            //System.out.println(nm + ": received STCA msg.\n");
                            stca_inform_content = new ArrayList<String>(Arrays.asList(stca_msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                            //System.out.println(stca_inform_content + " \n FD : STCA inform content \n");
                            if (stca_inform_content.get(0).equals(nm)) {
                                if (stca_inform_content.get(1).trim().equals("GREEN")) {
                                    System.out.println(nm + ": STCA msg GREEN.\n");
                                    List<String> announce_content = new ArrayList<>();
                                    ACLMessage announce = new ACLMessage(ACLMessage.INFORM);
                                    announce_content.add(nm);
                                    announce_content.add("RELEASED");
                                    landingAction();// landing taking place
                                    announce.setContent(announce_content.toString());
                                    //announce for both stca and airport
                                    announce.setConversationId("airport");
                                    announce.addReceiver(sdp_aid);
                                    announce.setConversationId("stca");
                                    announce.addReceiver(stca_aid);
                                    myAgent.send(announce);
                                    System.out.println(nm + " ==> INFORM (RELEASED) ==> " + stca_aid.getLocalName() + " .\n");
                                    System.out.println(nm + " ==> INFORM (RELEASED) ==> " + sdp_aid.getLocalName() + " .\n");
                                    System.out.println(nm + " landing operation FINISHED AND REPORTED.\n");
                                    myAgent.doDelete();
                                    phase = 5;

                                } else {
                                    System.out.println(nm + ": STCA msg  RED.\n");
                                    phase = 5;
                                }
                            } else {
                                //System.out.println(nm + ": SafetyAgent message is not addressed to self.\n");
                                phase = 5;
                            }

                        } else {
                            //System.out.println(nm + ": STCA did not respond.\n");
                            phase = 4;
                            //block();
                        }

                    } catch (Exception fe) {
                        fe.printStackTrace();
                    }
            }
        }

        public boolean done() {
            return (phase == 5);
        }
    }

    public int landingAction() {
        System.out.println(nm + ": Landing on progress.\n");
        return 0;
    }
}
