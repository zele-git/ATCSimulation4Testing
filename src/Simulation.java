import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import java.util.HashMap;

public class Simulation {

    public static void main(String[] args) {
//        Runtime rt = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "true");
        ContainerController cc = jade.core.Runtime.instance().createMainContainer(profile);
        AircraftNumber aircraftNumber = new AircraftNumber();

        AgentController managerController;
        try {
            managerController = cc.createNewAgent("Manager", "SDP_Agent", null);
            managerController.start();
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }

        AgentController stcaController;
        try {
            stcaController = cc.createNewAgent("SafetyAgent", "STCA_Agent", null);
            stcaController.start();
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < aircraftNumber.getAN(); i++) {
            AgentController amController;
            try {
                amController = cc.createNewAgent("Aircraft 0" + i, "FD_Agent", null);
                amController.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }

    }

    public static class AircraftNumber{
        private int AN = 5;

        AircraftNumber(){}

        public int getAN() {
            return AN;
        }

        public void setAN(int AN) {
            this.AN = AN;
        }

    }

    public static class MsgContainer{
        MsgContainer(){}
        private HashMap<String, String> rqstq = new HashMap<String, String>();
        public HashMap<String, String> getRqstq() {
            return rqstq;
        }

        public void setRqstq(HashMap<String, String> rqstq) {
            this.rqstq = rqstq;
        }



    }
}
