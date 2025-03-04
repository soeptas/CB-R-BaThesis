package deltaiot.cbam;

import java.util.ArrayList;
import java.util.List;

import deltaiot.types.Link;

public class AdaptationsCollector {
	
	/**
	 * Class keeping track of all adaptations. 
	 * Each adaptation option is defined for a specific link.
	 * 
	 * In this class there is the method for calculating the cost
	 * of sending packets (with adaptation info) in the upstream.
	 * The paths in the upstream are hard-coded (shortest-path)
	 */

	private static List<Adaptation> adaptations = new ArrayList<Adaptation>();
	private static boolean feedbackLoopCompleted = false;
	
	public static List<Adaptation> getAdaptations(){
		return adaptations;
	}
	
	public static void addAdaptation(Adaptation adaptation) {
		if (feedbackLoopCompleted) { // TODO: nagaan of dit niet door multithreading fout kan gaan
			//System.out.println("Previous Adaptation Processed" + adaptations.size() + "   -> Adaptations emptied");
			adaptations = new ArrayList<Adaptation>();
			feedbackLoopCompleted = false;
		}
		adaptations.add(adaptation);
	}
	
	public static void feedBackLoopCompleted() {
		feedbackLoopCompleted = true;
	}
	
	public static List<Link> getUniqueLinks() {
		List<Link> links = new ArrayList<>();
		for (Adaptation adaptation : adaptations){
			boolean unique = true;
			for (Link link : links) {
				if (adaptation.getLink().getSource() == link.getSource() &&
						adaptation.getLink().getDestination() == link.getDestination()) {
					unique = false;
				}
			}
			if (unique) {
				links.add(adaptation.getLink());
			}
		}
		return links;
	}
	
	
	
	/************************************************
	 * 				  NAIVE VERSION 0
	 *************************************************/
	// Naive function using constant amount of energy consumption for
	// each adaptation
	// Does NOT use piggy backing: 2 adaptation for the same link are NOT sent 
	// in the same timeslot(s)
	private static double getAdaptationsEnergyConsumptionV0() {
		double energyConsumption = 0;
		for (Adaptation adaptation : adaptations) {
			energyConsumption += calculateEnergyConsumption(adaptation.getLink()); 
		}
		return energyConsumption;
	}
	
	
	
	/************************************************
	 * 				  NAIVE VERSION 1
	 *************************************************/
	// Naive function using constant amount of energy consumption for 
	// each unique link that is used
	// Uses piggy backing: 2 adaptation for the same link are sent 
	// in the same timeslot(s)
	private static double getAdaptationsEnergyConsumptionV1() {
		double energyConsumption = 0;
		for (Link link : getUniqueLinks()) {
			energyConsumption += calculateEnergyConsumption(link); 
		}
		return energyConsumption;
	}
		
	
	public static double getAdaptationsEnergyConsumption(int version) {
		double adaptationEnergyConsumption;
		switch(version) {
			case 0 : adaptationEnergyConsumption = getAdaptationsEnergyConsumptionV0();
					 break;
			case 1 : adaptationEnergyConsumption = getAdaptationsEnergyConsumptionV1();
					 break;
			default: adaptationEnergyConsumption = getAdaptationsEnergyConsumptionV0();
					 break;
		}
		
		return adaptationEnergyConsumption;
	}
	
	
	private static double getReceiveBatteryUsage(int timeSlots) {
		double batteryUsage = timeSlots * DomainConstants.receptionTime
				* (DomainConstants.receptionCost / DomainConstants.coulomb);
		return batteryUsage;
	}

	
	private static double getSendBatteryUsageV0() {
		double batteryUsage = DomainConstants.getPacketDuplicationSfTime()
				* (DomainConstants.getPacketDuplicationConsumptionRate() / DomainConstants.coulomb);
		return batteryUsage;
	}
	
	
	private static double getSendBatteryUsagevV1() {
		double batteryUsage = DomainConstants.getSfTime(12) // TODO: dit aangepast
				* (DomainConstants.getPacketDuplicationConsumptionRate() / DomainConstants.coulomb);
		return batteryUsage;
	}
	
	
	private static double getSendBatteryUsage() {
		int sendBatteryFunction = 0;
		if (RunStatisticResults.getExtraArguments() != null ) {
			for (String argument : RunStatisticResults.getExtraArguments()) {
				if (argument.contains("SendBatteryFunction=")) {
					sendBatteryFunction = Integer.parseInt(argument.substring(argument.length() - 1));
				}
			}
		}
		
		double batteryUsage;
		switch(sendBatteryFunction) {
			case 0 : batteryUsage = getSendBatteryUsageV0();
					 break;
			case 1 : batteryUsage = getSendBatteryUsagevV1();
					 break;
			default: batteryUsage = getSendBatteryUsageV0();
					 break;
		}
		
		return batteryUsage;	
	}
	
	
	private static List<Integer> getPath(int sourceId){
		List<Integer> path = new ArrayList<>();
		switch(sourceId) {
			case 2 : path.add(4); path.add(2);
				     break;
			case 3 : path.add(3);
					 break;
			case 4 : path.add(4);
					 break;
			case 5 : path.add(9); path.add(5);
				 	 break;
			case 6 : path.add(4); path.add(6);
					 break;
			case 7 : path.add(3); path.add(7);
					 break;
			case 8 : path.add(8);
					 break;
			case 9 : path.add(9);
					 break;
			case 10: path.add(4); path.add(6); path.add(10); // Of 9-5-10 (5 is RFID)
					 break;
			case 11: path.add(3); path.add(7); path.add(11);
					 break;
			case 12: path.add(3); path.add(12);
			 		 break;
			case 13: path.add(3); path.add(7); path.add(11); path.add(13);
					 break;
			case 14: path.add(3); path.add(12); path.add(14);
					 break;
			case 15: path.add(3); path.add(12); path.add(15);
			 		 break;
            default: break;
		}
		
		return path;
	}
	
	
	private static double calculateEnergyConsumption(Link link) throws IllegalArgumentException {
		List<Integer> path = getPath(link.getSource());
		
		double adaptationEnergyConsumption = 0;
		
		final int TIMESLOTS = RunStatisticResults.getTimeSlots(); // TODO: IS THIS OK??? NORMALLY ONLY 1
		
		if (path.size() == 0) {
			throw new IllegalArgumentException("Invalid link (there is no path for this link encoded)");
		}
		else if (path.size() == 1) { // Only implement the receive cost (actually the else also covers this)
			adaptationEnergyConsumption += getReceiveBatteryUsage(TIMESLOTS);
		}
		else { // Calculate both send (except for the last mote) and receive cost
			for (int i = 0; i < path.size(); i++) {
				if (i < path.size() - 1) { // The last mote does not have a sendBatteryUsage (this is because the first send (gateway -> mote 1) is powered by the AC net 
					adaptationEnergyConsumption += getSendBatteryUsage();
				}
				adaptationEnergyConsumption += getReceiveBatteryUsage(TIMESLOTS);
			}
		}
		
		return adaptationEnergyConsumption;
	}
	
	
	/*
	public static double getMotePowerConsumption(int moteID, int version) {
		double powerConsump = 0;
		
		final int TIMESLOTS = RunStatisticResults.getTimeSlots(); // TODO: IS THIS OK??? NORMALLY ONLY 1
	
		// Adaptation power consump
		for (Adaptation adapt: adaptations) {
			if (adapt.getLink().getSource() == moteID) {
				powerConsump += getReceiveBatteryUsage(TIMESLOTS);
			}
			else {
				List<Integer> path = getPath(adapt.getLink().getSource());
				if (path.contains(moteID)) {
					powerConsump += getReceiveBatteryUsage(TIMESLOTS) + 
							getSendBatteryUsage();
				}
			}
		}		
		return powerConsump;
	}
	*/
}
