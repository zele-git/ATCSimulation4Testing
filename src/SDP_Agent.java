import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.io.FileOutputStream;
import java.util.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

public class SDP_Agent extends Agent {

    private String serviceType = null;
    private String safetyagentname = null;

    private String nm;

    private List<List<String>> sdp_rprt = new ArrayList();
    WriteToFile wtf = new WriteToFile();

    private HashMap<String, String> rqstq = new HashMap<String, String>();

    private String runway = "FREE";
    private int count = 0;

    Simulation.AircraftNumber aircraftNumber = new Simulation.AircraftNumber();


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
        service.setName("landing_airport");
        service.setType("landing");
        dfd.addServices(service);
        System.out.println("service type: " + serviceType + "\n");
//        }
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {

                myAgent.addBehaviour(new ServeRequest());
                myAgent.addBehaviour(new ServeReplies());

            }
        });

    }

    protected void takeDown() {
        System.out.println(sdp_rprt + "\n");
        wtf.writeToExcel(sdp_rprt);
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("SDP " + nm + " terminating. \n");
    }

    private class ServeRequest extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            ACLMessage reply = null;
            List<String> cfp_content = null;
            List<String> inform_content = new ArrayList<>();
            List<String> sdp_status = new ArrayList<>();
            List<String> sdp_status2 = new ArrayList<>();
            List<String> sdp_status3 = new ArrayList<>();

            boolean flg = false;

            if (msg != null) {
                System.out.println("SDP: CFP not empty \n ");

                if (msg.getContent() != null) {
                    cfp_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                    Iterator<Map.Entry<String, String>>
                            iterator = rqstq.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<String, String> entry = iterator.next();
                        if (entry.getKey().equals(cfp_content.get(0)))
                            flg = true;
                    }
                    if (flg == false) {
                        rqstq.put(cfp_content.get(0), "WAITING");
                        //iterate
                        iterator = rqstq.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<String, String> entry = iterator.next();
                            if (entry.getKey().equals(cfp_content.get(0).trim())) {
                                sdp_status3.add(runway);
                                sdp_status3.add(cfp_content.get(0).trim());
                                sdp_status3.add(rqstq.get(cfp_content.get(0)));
                                sdp_status3.add(cfp_content.get(1));
                            }
                        }

                        sdp_rprt.add(sdp_status3);
                    }
                }
                System.out.println(rqstq+ "\n SDP:response to CFP ");

                if (rqstq != null) {
                    System.out.println("SDP:  Request list not empty \n ");

                    try {
                        reply = msg.createReply();
                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setConversationId("landing_aircraft");
                        //should only check if ACK is already granted
                        String randomaircraftname = (String) rqstq.keySet().toArray()[new Random().nextInt(rqstq.keySet().toArray().length)];
                        System.out.println(randomaircraftname + " random name\n");

                        if (runway.equals("FREE") && rqstq.get(randomaircraftname).trim().equals("WAITING")) {
                            //iterate
                            Iterator<Map.Entry<String, String>>
                                    iterator = rqstq.entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry<String, String> entry = iterator.next();
                                if (entry.getKey().equals(randomaircraftname)) {
                                    sdp_status.add(runway);
                                    sdp_status.add(randomaircraftname);
                                    sdp_status.add(entry.getValue());
                                    sdp_status.add(cfp_content.get(1));
                                }
                            }
                            inform_content.add(randomaircraftname);
                            inform_content.add("CLEARED");
                            reply.setContent(inform_content.toString());
                            myAgent.send(reply);
                            rqstq.put(randomaircraftname, "CLEARED");
                            runway = "BUSY";

                            sdp_rprt.add(sdp_status);
                            System.out.println(sdp_status + " \n SDP:container information\n");
                        }
                        if (runway.equals("BUSY") && rqstq.get(randomaircraftname).trim().equals("CLEARED")) {
                            Iterator<Map.Entry<String, String>>
                                    iterator = rqstq.entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry<String, String> entry = iterator.next();
                                if (entry.getKey().equals(randomaircraftname)) {
                                    sdp_status2.add(runway);
                                    sdp_status2.add(randomaircraftname);
                                    sdp_status2.add(entry.getValue());
                                    sdp_status.add(cfp_content.get(1));
                                }
                            }

                            inform_content.add(randomaircraftname);
                            inform_content.add("WAITING");
                            reply.setContent(inform_content.toString());
                            myAgent.send(reply);
                            rqstq.put(randomaircraftname, "WAITING");
                            runway = "FREE";

                            sdp_rprt.add(sdp_status2);
                            System.out.println(sdp_status + " \n SDP:container information\n");
                        }
                        //execution result

                    } catch (Exception e) {
                        System.out.println(e + "error00\n");
                    }

                } else {
                    System.out.println("SDP: Request list empty \n ");
                    block();
                }
            } else {
                System.out.println("SDP: CFP empty \n ");
                block();
            }
        }

    }

    private class ServeReplies extends CyclicBehaviour {
        List<String> inform_content = new ArrayList<>();

        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            List<String> sdp_status = new ArrayList<>();

            if (msg != null) {
                inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                if (inform_content.get(1).trim().equals("RELEASED")) {
                    rqstq.remove(inform_content.get(0));
//                    runway = "FREE";
                    count++; //controlling whether all airplane have accomplished their goal
                    sdp_status.add("FREE");
                    sdp_status.add(inform_content.get(0).trim());
                    sdp_status.add(rqstq.get(inform_content.get(0).trim()));
                    sdp_status.add(inform_content.get(1).trim());

                    sdp_rprt.add(sdp_status);
                    System.out.println(sdp_status + " \n SDP: container information\n");

                }
                if (inform_content.get(1).trim().equals("ACK")) {
                    // raise flag for STCA
                    rqstq.put(inform_content.get(0).trim(), "CONFIRMED");
                    sdp_status.add(runway);
                    sdp_status.add(inform_content.get(0).trim());
                    sdp_status.add(rqstq.get(inform_content.get(0).trim()));
                    sdp_status.add(inform_content.get(1).trim());

                    sdp_rprt.add(sdp_status);
                    System.out.println("SDP: communicate STCA \n");
                    // Search STCA, and INFORM
                    System.out.println("SDP: communicate STCA &&&&&&& \n" + rqstq);

                    addBehaviour(new SafetyCheckAgent(rqstq));
                }
                System.out.println("++++++++++++++ SDP: received acknowledgement. \n");
                System.out.println(inform_content + " ACK content. \n");

            } else {
                System.out.println("-------------- SDP: not yet received acknowledgement. \n");
                block();
            }
            if (count == aircraftNumber.getAN()) { //number of aircraft reported RELEASED
                myAgent.doDelete();
            }
        }
    }

    private class SafetyCheckAgent extends Behaviour {

        SafetyCheckAgent(HashMap<String, String>  rqstq ){
            HashMap<String, String>  container = rqstq;
        }
        private AID[] safety_agents;
        private MessageTemplate mt;
        private int phase = 0;

        public void action() {
            switch (phase) {
                case 0:
                    System.out.println(nm + " searching for Safety Agent.\n");
                    DFAgentDescription template1 = new DFAgentDescription();
                    ServiceDescription sd1 = new ServiceDescription();
                    sd1.setName("safety_check");
                    sd1.setType("safety");
                    template1.addServices(sd1);
//                    String agent_name = null;
                    try {
                        // search for safety agent
                        DFAgentDescription[] result = DFService.search(myAgent, template1);
                        safety_agents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            safety_agents[i] = result[i].getName();
                            safetyagentname = result[i].getName().toString();
                        }
                        if (safetyagentname != null) {
                            System.out.println(nm + " found the following SafetyAgent: " + safetyagentname + "\n");
                            phase = 1;
                        } else {
                            System.out.println(nm + " no SafetyAgent nearby.\n");
                            phase = 0;
                        }
                    } catch (Exception fe) {
                        fe.printStackTrace();
                    }

                    break;
                case 1:
                    List<String> cfp_msg = new ArrayList<>();
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    try {
                        for (int i = 0; i < safety_agents.length; ++i) {
                            cfp.addReceiver(safety_agents[i]);
                        }
                        cfp_msg.add(nm);
                        cfp_msg.add("UP");
                        System.out.println(nm + " &&&&&&&&&&&&&: \n" + rqstq + " .\n");

                        cfp_msg.add(rqstq.toString());
                        cfp.setContent(cfp_msg.toString());
                        cfp.setConversationId("safety_check");
                        cfp.setReplyWith("cfp " + System.currentTimeMillis());
                        myAgent.send(cfp);
                        System.out.println(nm + " submitted CFP to STCA:" + safetyagentname + " .\n");
                        phase = 2;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if (phase != 2) {
                        System.out.println(nm + " NOT submitted CFP to STCA. \n");
                        phase = 0;
                    }
                    break;
            }
        }

        public boolean done() {
            return (phase == 2);
        }
    }

    private class WriteToFile {
        public void writeToExcel(List<List<String>> input) {
            try {
                FileOutputStream out = new FileOutputStream(new File("C:\\jade\\ATC_Simulation\\rprt.xls"));

                HSSFWorkbook workbook = new HSSFWorkbook();
                HSSFSheet sheet = workbook.createSheet("ATC statistics");

                Iterator<List<String>> i = input.iterator();
                Row headerrow = sheet.createRow(0);
                Cell ac00 = headerrow.createCell(0);
                ac00.setCellValue("Runway status");
                Cell ac01 = headerrow.createCell(1);
                ac01.setCellValue("Aircracraft code");
                Cell ac02 = headerrow.createCell(2);
                ac02.setCellValue("Landing status");
                Cell ac03 = headerrow.createCell(3);
                ac03.setCellValue("Aircraft Msg");

                int rownum = 1;
                int cellnum = 0;
                while (i.hasNext()) {
                    List<String> templist = (List<String>) i.next();
                    Iterator<String> tempIterator = templist.iterator();
                    Row row = sheet.createRow(rownum++);
                    cellnum = 0;
                    while (tempIterator.hasNext()) {
                        String temp = (String) tempIterator.next();
                        Cell cell = row.createCell(cellnum++);
                        cell.setCellValue(temp);
                    }
                }
                workbook.write(out);
                out.close();
                workbook.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }
}
