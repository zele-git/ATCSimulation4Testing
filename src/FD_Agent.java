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
    private AID[] safety_agents;
    private String airportname = null;
    private String serviceType = null;

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
        serviceType = "landing";

        System.out.println(nm + " starting. \n");
        service.setName("landing_aircraft");
        service.setType("landing");
        dfd.addServices(service);
        System.out.println("service type: " + serviceType + "\n");
//        }
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new LandingOperation());


    }

    protected void takeDown() {
        System.out.println(nm + " terminating. \n");
    }

    private class LandingOperation extends Behaviour {
        private MessageTemplate mt;
        private int phase = 0;
        List<String> inform_content = null;

        public void action() {
            switch (phase) {
                case 0:
                    //search for airport
                    System.out.println(nm + " searching for airport.\n");
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setName("landing_airport");
                    sd.setType("landing");
                    template.addServices(sd);
                    try {
                        // search for safety agent
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        airports = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            airports[i] = result[i].getName();
                            airportname = result[i].getName().toString();
                        }
                        if (airportname != null) {
//                            myAgent.addBehaviour(new submitLandingRequest());
                            System.out.println(nm + " found the following airports: " + airportname + "\n");
                            phase = 1;
                        } else {
                            System.out.println(nm + " no airport nearby.\n");
//                            myAgent.doDelete();
                            phase = 0;
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    break;
                case 1:
                    //send cfp to all all airports or SDP
                    List<String> cfp_msg = new ArrayList<>();
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    try {
                        for (int i = 0; i < airports.length; ++i) {
                            cfp.addReceiver(airports[i]);
                        }
                        cfp_msg.add(nm);
                        cfp_msg.add("RQST");
                        cfp.setContent(cfp_msg.toString());
                        cfp.setConversationId("landing_aircraft");
                        cfp.setReplyWith("cfp " + System.currentTimeMillis());
                        myAgent.send(cfp);
                        System.out.println(nm + " submitted CFP to " + airportname + "\n");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    //prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("landing_aircraft"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

                    phase = 2;
                    break;
                case 2:
                    //receive responses from SDP
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    ACLMessage msg = myAgent.receive(mt);
                    if (msg != null) {
                        System.out.println("FD: SDP replay is not empty. \n");
                        if (msg.getPerformative() == ACLMessage.INFORM) {
                            inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                            if (nm.equals(inform_content.get(0))) {
                                System.out.println(nm + " ready to notify for landing. \n");
                            }
                        }

                    } else {
                        System.out.println("FD: SDP replay is empty. \n");
                        block();
                    }
                    phase = 3;
                    break;
                case 3:
                    //send ACK to airport
                    mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    msg = myAgent.receive(mt);
                    ACLMessage ack = null;
                    try {
                        List<String> inform_content = new ArrayList<>();
                        if (msg != null) {
                            System.out.println(nm + " ++++++ FD: received permission.\n");

                            inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                            System.out.println(inform_content + "inform content \n");
                            if (nm.equals(inform_content.get(0).trim()) && inform_content.get(1).trim().equals("CLEARED")) {
                                ack = msg.createReply();
                                ack.setPerformative(ACLMessage.INFORM);
                                List<String> inform = new ArrayList<>();
                                inform.add(nm);
                                inform.add("ACK");
                                ack.setContent(inform.toString());
                                ack.setReplyWith("claim" + System.currentTimeMillis());
                                myAgent.send(ack);
                                System.out.println(nm + " landing operation on progress.\n");
                                phase = 4;
                            } else {
                                phase = 1;
                            }

                        } else {
                            System.out.println(nm + " ----------------  FD not yet received permission.\n");
                            block();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    break;
                case 4:
                    //check for safety, first search for safety agent
                    System.out.println(nm + " searching for Safety Agent.\n");
                    DFAgentDescription template1 = new DFAgentDescription();
                    ServiceDescription sd1 = new ServiceDescription();
                    sd1.setName("safety_check");
                    sd1.setType("safety");
                    template1.addServices(sd1);
                    String agent_name = null;
                    try {
                        // search for airports
                        DFAgentDescription[] result = DFService.search(myAgent, template1);
                        safety_agents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            safety_agents[i] = result[i].getName();
                            agent_name = result[i].getName().toString();
                        }
                        if (agent_name != null) {
                            System.out.println(nm + " found the following SafetyAgent: " + agent_name + "\n");
                            phase = 5;
                        } else {
                            System.out.println(nm + " no SafetyAgent nearby.\n");
                            phase = 4;
                            break;
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    break;

                case 5:
                    //GET STCA permission
                    inform_content = new ArrayList<>();
                    mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                    msg = myAgent.receive(mt);
                    try {
//                        mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                        if (msg != null) {
                            System.out.println(nm + " ++++++++++++ FD received STCA msg.\n");
                            inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                            System.out.println(inform_content + "inform content \n");
                            if (inform_content.get(0).equals(nm) && inform_content.get(1).trim().equals("GREEN")) {
                                phase = 6;
                                System.out.println(nm + " :-----  GREEN.\n");
                                break;
                            } else {
                                System.out.println(nm + ":------  STCA msg RED.\n");
                                phase = 1;
                                break;
                            }
                        } else {
                            System.out.println(nm + ":------  STCA DID NOT RESPOND.\n");
                            phase = 1;
                            break;
                        }
                    } catch (Exception fe) {
                        fe.printStackTrace();
                    }
                case 6:
                    //send ACK to airport
                    mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    msg = myAgent.receive(mt);
                    ACLMessage reply = null;
                    try {
                        inform_content = new ArrayList<>();
                        if (msg != null) {
                            System.out.println(nm + ":+++++ FD received STCA permission.\n");

                            inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                            System.out.println(inform_content + "inform content \n");
                            if (nm.equals(inform_content.get(0).trim()) && inform_content.get(1).trim().equals("CLEARED")) {
                                reply = msg.createReply();
                                reply.setPerformative(ACLMessage.INFORM);
                                List<String> inform = new ArrayList<>();
                                inform.add(nm);
                                inform.add("RELEASED");
                                landingAction();// landing taking place
                                reply.setContent(inform.toString());
                                reply.setReplyWith("claim" + System.currentTimeMillis());
                                myAgent.send(reply);
                                System.out.println(nm + " landing operation FINISHED AND REPORTED.\n");
                                myAgent.doDelete();
                                phase = 7;
                            } else {
                                phase = 1;
                            }

                        } else {
                            System.out.println(nm + " ----------------  FD not yet received permission.\n");
                            block();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    break;

            }

        }

        public boolean done() {

            return (phase == 7);
        }
    }

    public int landingAction() {
        System.out.println(nm + " Landing on progress.\n");
        return 0;
    }
}
