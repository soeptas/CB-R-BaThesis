package deltaiot.activforms;

import static deltaiot.activforms.Settings.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import activforms.engine.ActivFORMSEngine;
import activforms.types.Int;
import deltaiot.cbam.ProcessError;
import deltaiot.cbam.RunStatisticResults;
import deltaiot.client.Probe;
import deltaiot.services.Link;
import deltaiot.services.Mote;
import deltaiot.services.QoS;

public class ProbeConnector {
	ActivFORMSEngine engine;
	int monitor;
	
	Probe probe;
	HashMap deltaIoTModel;
	public void sendMessageToFeedbackLoop(List<Mote> motes, QoS qos) {

		// Prepare message to send		
		/*List<String> data = new ArrayList<>();
		int id, i;
		for(Mote mote: motes){
			id = mote.getMoteid();
			data.add(String.format("deltaIoT.motes[%d].moteId=%d;", id, id));
			data.add(String.format("deltaIoT.motes[%d].load=%d;", id, mote.getLoad()));
			data.add(String.format("deltaIoT.motes[%d].energyLevel=%d;", id, Math.round(mote.getBattery())));
			data.add(String.format("deltaIoT.motes[%d].parents=%d;", id, mote.getParents()));
			data.add(String.format("deltaIoT.motes[%d].dataProbability=%d;", id, mote.getDataProbability()));
			data.add(String.format("deltaIoT.motes[%d].queueSize=%d;", id, mote.getQueueSize()));
			i = 0;
			for(Link link: mote.getLinks()){
				data.add(String.format("deltaIoT.motes[%d].links[%d].source=%d;", id, i,link.getSource()));
				data.add(String.format("deltaIoT.motes[%d].links[%d].destination=%d;", id, i,link.getDest()));
				data.add(String.format("deltaIoT.motes[%d].links[%d].powerSetting=%d;", id, i,link.getPower()));
				data.add(String.format("deltaIoT.motes[%d].links[%d].packetLoss=%d;", id, i,link.getPacketLoss()));
				data.add(String.format("deltaIoT.motes[%d].links[%d].SNR=%d;", id, i,Math.round(link.getSNR())));
				data.add(String.format("deltaIoT.motes[%d].links[%d].distribution=%d;", id, i,link.getDistribution()));
				i++;
			}
		}*/
		
		// Copy QoS properties
		((Int) ((HashMap)deltaIoTModel.get("qos")).get("packetLoss")).setValue(toInt(qos.getPacketLoss()));
		((Int) ((HashMap)deltaIoTModel.get("qos")).get("energyConsumption")).setValue(toInt(qos.getEnergyConsumption()));
		
		// Copy Mote properties
		for(Mote mote: motes){
			HashMap<String, Int> moteModel = (HashMap<String, Int>) ((HashMap)deltaIoTModel.get("motes")).get(mote.getMoteid());
			moteModel.get("moteId").setValue(mote.getMoteid());
			moteModel.get("load").setValue(mote.getLoad());
			moteModel.get("energyLevel").setValue(((Double)mote.getBattery()).intValue());
			moteModel.get("parents").setValue(mote.getParents());
			moteModel.get("dataProbability").setValue(mote.getDataProbability());
			moteModel.get("queueSize").setValue(mote.getCurrentQSize());


			// Set Link properties
			int i = 0;
			for(Link link: mote.getLinks()){
				HashMap<String, Int> linkMote = (HashMap<String, Int>) ((HashMap)(Object)moteModel.get("links")).get(i++);
				
				linkMote.get("source").setValue(link.getSource());
				linkMote.get("destination").setValue(link.getDest());
				linkMote.get("powerSetting").setValue(link.getPower());
				linkMote.get("SNR").setValue(link.getSNR().intValue());
				linkMote.get("distribution").setValue(link.getDistribution());
				linkMote.get("SF").setValue(link.getSF());
			}
		}
		
		// send signal
		try {
			engine.send(monitor);//, data.toArray(new String[data.size()]));
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			ex.printStackTrace(ProcessError.getErrorPrintStream());
		}
	}

	public void setEngine(ActivFORMSEngine engine) {
		this.engine = engine;

		// receive monitor channel identifier
		monitor = engine.getChannel("monitor");
		deltaIoTModel = (HashMap) engine.getData("&deltaIoT").get("&deltaIoT");
	}

	public ActivFORMSEngine getEngine() {
		return engine;
	}

	public void start() {
		thread.start();
	}

	Thread thread = new Thread(new Runnable() {

		@Override
		public void run() {
			int runs = 1000;
			for(int i = 0; i < runs; i++) {
				startTime = System.currentTimeMillis();
				List<Mote> motes = probe.getAllMotes();
				List<QoS> qosList = probe.getNetworkQoS(1);
				adaptationDone.set(false);
				System.out.println("Sending message to feedback loop at time:" + System.currentTimeMillis());
				sendMessageToFeedbackLoop(motes, qosList.get(0));
				waitAdaptationPerformed();
				RunStatisticResults.writeDataToFile(qosList.get(0), (HashMap) deltaIoTModel.get("motes"));
				try {
					Thread.currentThread().sleep(570000);

				} catch (InterruptedException e) {
					e.printStackTrace(System.err);
					//e.printStackTrace(ProcessError.getErrorPrintStream());
				}
			}
			probe.getNetworkQoS(runs).forEach(qos -> System.out.println(qos));
		}
	});

	public void setProbe(Probe probe) {
		this.probe = probe;
	}

	void waitAdaptationPerformed() {
		while(Settings.adaptationDone.get() == false){
			try {
				Thread.currentThread().sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				//e.printStackTrace(ProcessError.getErrorPrintStream());
			}
		}
	}
}
