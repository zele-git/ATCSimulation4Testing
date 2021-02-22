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
    private AID fd_aid;
    private AID stca_aid;


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
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription service = new ServiceDescription();
        System.out.println(nm + ": starting. \n");
        service.setName("airport");
        service.setType("manager");
        dfd.addServices(service);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                myAgent.addBehaviour(new ServeRequest());
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
        System.out.println(nm + ": terminating. \n");
    }

    private class ServeRequest extends Behaviour {
        private int phase = 0;
        List<String> inform_content = new ArrayList<>();
        private AID[] safety_agents;

        public void action() {

            MessageTemplate mt = null;
            ACLMessage msg = null;
            ACLMessage reply = null;
            List<String> cfp_content = null;
//            List<String> inform_content = new ArrayList<>();
//            List<String> sdp_status = new ArrayList<>();
//            List<String> sdp_status2 = new ArrayList<>();
//            List<String> sdp_status3 = new ArrayList<>();

            boolean flg = false;
            switch (phase) {
                case 0: //get request from fd, check if fd exist, if it did not exist add to WAITING
                    mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                    msg = myAgent.receive(mt);
                    if (msg != null) {
                        fd_aid = msg.getSender();
                        //System.out.println(nm + ": FD INFORM content NOT empty. \n " + msg.getSender());
//                        System.out.println(nm + ": FD INFORM content NOT empty. \n " + myAgent.getLocalName());
//                        System.out.println(nm + ": FD INFORM content NOT empty. \n " + myAgent.getAID());
//                        System.out.println(nm + ": FD INFORM content NOT empty. \n " + myAgent.getName());

                        inform_content = new ArrayList<String>(Arrays.asList(msg.getContent().replaceAll("\\[|\\]", "").split(",")));
                        //check if rqstq contain any content with the coming id
                        if (rqstq.size() > 0) {
                            Iterator<Map.Entry<String, String>>
                                    iterator = rqstq.entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry<String, String> entry = iterator.next();
                                if (entry.getKey().equals(inform_content.get(0)))
                                    flg = true;
                            }
                            if (flg == false) {// doesnt contain a request
                                rqstq.put(inform_content.get(0), "WAITING");
                                //this is for reporting and ...
//                            iterator = rqstq.entrySet().iterator();
//                            while (iterator.hasNext()) {// this is for reporting
//                                Map.Entry<String, String> entry = iterator.next();
//                                if (entry.getKey().equals(inform_content.get(0).trim())) {
//                                    sdp_status3.add(runway);
//                                    sdp_status3.add(inform_content.get(0).trim());
//                                    sdp_status3.add(rqstq.get(inform_content.get(0)));
//                                    sdp_status3.add(inform_content.get(1));
//                                }
//                            }
//                            sdp_rprt.add(sdp_status3);
                            }
                           // System.out.println(rqstq + "\n SDP: Request container content.\n");
                            if (inform_content.get(1).trim().equals("RQST")) {// FD inform conent is RQST
                                //System.out.println(nm + ": is requesting . \n ");
                                String randomaircraftname = null;
                                try {
                                    //should only check if ACK is already granted, random selection
                                    if (rqstq.size() == 1) {
                                        randomaircraftname = (String) rqstq.keySet().toArray()[0];
                                    } else {
                                        randomaircraftname = (String) rqstq.keySet().toArray()[new Random().nextInt(rqstq.keySet().toArray().length)];
                                    }
                                } catch (Exception e) {
                                    System.out.println(e + " ERROR==00 .\n");
                                }
                                if (rqstq.get(randomaircraftname).trim().equals("WAITING")) {
                                    //this is for reporting
//                                        iterator = rqstq.entrySet().iterator();
//                                        while (iterator.hasNext()) { // for reporting
//                                            Map.Entry<String, String> entry = iterator.next();
//                                            if (entry.getKey().equals(randomaircraftname)) {
//                                                sdp_status.add(runway);
//                                                sdp_status.add(randomaircraftname);
//                                                sdp_status.add(entry.getValue());
//                                                //sdp_status.add(cfp_content.get(1));
//                                            }
//                                        }
//                                        sdp_rprt.add(sdp_status);
                                    //reply = msg.createReply();
                                    try {
                                        List<String> info_content = new ArrayList<>();
                                        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                                        info_content.add(randomaircraftname);
                                        info_content.add("CLEARED");
                                        inform.setContent(info_content.toString());
                                        inform.setConversationId("aircraft");

                                        inform.addReceiver(fd_aid);
                                        myAgent.send(inform);
                                        rqstq.put(randomaircraftname, "CLEARED");
                                        runway = "BUSY";
                                        phase = 1;
                                        System.out.println(nm + " ==> INFORM (CLEARED) ==> " + randomaircraftname + " .\n");

                                        //System.out.println(sdp_status + " \n SDP:container information\n");
                                    }catch (Exception e) {
                                        System.out.println(e + " ERROR==11 .\n");
                                    }
                                }
                                if (runway.equals("BUSY") && rqstq.get(randomaircraftname).trim().equals("CLEARED")) {
                                    //this is for reporting
//                                        iterator = rqstq.entrySet().iterator();
//                                        while (iterator.hasNext()) {
//                                            Map.Entry<String, String> entry = iterator.next();
//                                            if (entry.getKey().equals(randomaircraftname)) {
//                                                sdp_status2.add(runway);
//                                                sdp_status2.add(randomaircraftname);
//                                                sdp_status2.add(entry.getValue());
////                                        sdp_status.add(cfp_content.get(1));
//                                            }
//                                        }
//                                        sdp_rprt.add(sdp_status2);
//                                        System.out.println(sdp_status + " \n SDP:container information\n");
                                    //reply = msg.createReply();
                                    try {
                                        List<String> info_content = new ArrayList<>();
                                        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);

                                        inform.setConversationId("aircraft");
                                        info_content.add(randomaircraftname);
                                        info_content.add("WAITING");
                                        inform.setContent(info_content.toString());
                                        inform.addReceiver(fd_aid);
                                        myAgent.send(inform);
                                        rqstq.put(randomaircraftname, "WAITING");
                                        runway = "FREE";
                                        phase = 1;
                                        System.out.println(nm + " ==> INFORM (WAITING) ==> " + randomaircraftname + " .\n");

                                    }catch (Exception e) {
                                        System.out.println(e + " ERROR==22 .\n");
                                    }
                                }

                            } else {
                               // System.out.println(nm + " not RQST .\n");
                                if (inform_content.get(1).trim().equals("ACK")) {
                                    rqstq.put(inform_content.get(0).trim(), "CONFIRMED");
//                            sdp_status.add(runway);
//                            sdp_status.add(inform_content.get(0).trim());
//                            sdp_status.add(rqstq.get(inform_content.get(0).trim()));
//                            sdp_status.add(inform_content.get(1).trim());
//
//                            sdp_rprt.add(sdp_status);
                                   // System.out.println("SDP: communicate STCA \n");
                                    // Search STCA, and INFORM
                                    //System.out.println("SDP: communicate STCA &&&&&&& \n" + rqstq);
                                    phase = 1;

                                } else {

                                    if (inform_content.get(1).trim().equals("RELEASE")) {
                                        rqstq.remove(inform_content.get(0));
                                        count++; //controlling whether all airplane have accomplished their goal
//                            sdp_status.add("FREE");
//                            sdp_status.add(inform_content.get(0).trim());
//                            sdp_status.add(rqstq.get(inform_content.get(0).trim()));
//                            sdp_status.add(inform_content.get(1).trim());
//
//                            sdp_rprt.add(sdp_status);
//                            System.out.println(sdp_status + " \n SDP: container information\n");
                                        phase = 0;
                                        if (count == aircraftNumber.getAN()) { //number of aircraft reported RELEASED
                                            myAgent.doDelete();
                                        }
                                    }
                                }
                            }

                        } else {
//                                if (inform_content.get(1).trim().equals("RQST")) {// FD inform conent is RQST
                            rqstq.put(inform_content.get(0), "CLEARED");
                            System.out.println(rqstq + "\n Request container content.\n");
                            phase = 1;
//                                }
                        }             //

                    } else {
                        //System.out.println("-------------- SDP: not yet received INFORM  from FD. \n");
                        //block();
                        if (rqstq.size() > 0) {
                            // randomly select one WAITING and make CONFIRM
                            //System.out.println("-------------- SDP: RQSTQ  NOT empty. \n");
                            try {
                                String randomaircraftname = null;
                                if (rqstq.size() == 1) {
                                    randomaircraftname = (String) rqstq.keySet().toArray()[0];
                                    //System.out.println(randomaircraftname + " the only single one aircraft\n");
                                } else {
                                    randomaircraftname = (String) rqstq.keySet().toArray()[new Random().nextInt(rqstq.keySet().toArray().length)];
                                    //System.out.println(randomaircraftname + " selected among others\n");

                                }
                                rqstq.put(randomaircraftname, "CONFIRMED");
                                phase = 1;
                                System.out.println(rqstq + "\n Request container content.\n");

                            } catch (Exception fe) {
                                fe.printStackTrace();
                            }
                        } else {
                            //System.out.println("-------------- SDP: RQSTQ empty. \n");
                            block();
//                            phase = 0;
                        }
                    }

                case 1://locate STCA
                    System.out.println(nm + " searching for Safety Agent.\n");
                    DFAgentDescription template1 = new DFAgentDescription();
                    ServiceDescription sd1 = new ServiceDescription();
                    sd1.setName("stca");
                    sd1.setType("safety");
                    template1.addServices(sd1);
                    List<String> safetyagent_name = new ArrayList<>();
                    try {
                        // search for safety agent
                        DFAgentDescription[] result = DFService.search(myAgent, template1);
                        safety_agents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            safety_agents[i] = result[i].getName();
                            stca_aid = result[i].getName();
                            safetyagent_name.add(result[i].getName().toString());
                        }
                        if (safetyagent_name != null) {
                           // System.out.println(nm + ": found the following SafetyAgent: " + stca_aid + "\n");
                            List<String> cfp_msg = new ArrayList<>();
                            ACLMessage cfp = new ACLMessage(ACLMessage.INFORM);
                            try {
                                cfp_msg.add(nm);
                                cfp_msg.add("UP");
                                System.out.println(nm + " LIST TO BE CHECKED OUT: \n" + rqstq + " .\n");
                                cfp_msg.add(rqstq.toString());
                                cfp.setContent(cfp_msg.toString());
                                cfp.setConversationId("stca");
                                cfp.addReceiver(stca_aid);
                                myAgent.send(cfp);
                                System.out.println(nm + " ==> INFORM (UP) ==> " + stca_aid.getLocalName() + " .\n");
                                phase = 2;
                                break;
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                //System.out.println(nm + " INFORM NOT submitted to STCA. \n");
                                //block();
                            }
                        } else {
                            //System.out.println(nm + " no SafetyAgent nearby.\n");
                            block();
//                            phase = 1;
                        }
                    } catch (Exception fe) {
                        fe.printStackTrace();
                    }

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
