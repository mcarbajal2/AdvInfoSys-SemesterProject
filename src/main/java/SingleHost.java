import java.util.ArrayList;
import java.util.List;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.util.Log;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;

/**
 * A CloudSim Plus simulation to compare and analyze
 * single-host Data Center power and efficiency trade-offs.
 */
public class SingleHost {

	public static void main(String[] args) {
		Log.setLevel(Level.ERROR);

		CloudSimPlus simulation = new CloudSimPlus();

		//Create the 3 data centers
		createSingleHostLegacy(simulation);
		createSingleHostModern(simulation);
		createSingleHostAI(simulation);

		//Create dedicated brokers
		DatacenterBroker brokerLegacy = new DatacenterBrokerSimple(simulation);
		DatacenterBroker brokerModern = new DatacenterBrokerSimple(simulation);
		DatacenterBroker brokerAI = new DatacenterBrokerSimple(simulation);

		//Create VMs and connect to brokers
		//Legacy VM: 1000 MIPS, 2 PEs, 2048 MB RAM, 10 Mbps BW, 36000 MB Storage
		Vm vmLegacy = new VmSimple(1000, 2).setRam(2048).setBw(10).setSize(36000)
				.setCloudletScheduler(new CloudletSchedulerSpaceShared());
		vmLegacy.setId(1);

		//Modern VM: 2500 MIPS, 56 PEs, 98304 MB RAM, 4000 Mbps BW, 340000 MB Storage
		Vm vmModern = new VmSimple(2500, 56).setRam(98304).setBw(4000).setSize(340000)
				.setCloudletScheduler(new CloudletSchedulerSpaceShared());
		vmModern.setId(2);

		//AI VM: 15000 MIPS, 128 PEs, 3072000 MB RAM, 4000 Mbps BW, 122880000 MB Storage
		Vm vmAI = new VmSimple(15000, 128).setRam(3072000).setBw(4000).setSize(122880000)
				.setCloudletScheduler(new CloudletSchedulerSpaceShared());
		vmAI.setId(3);

		brokerLegacy.submitVmList(List.of(vmLegacy));
		brokerModern.submitVmList(List.of(vmModern));
		brokerAI.submitVmList(List.of(vmAI));

		//Submit tasks
		createAndSubmitCloudlets(brokerLegacy);
		createAndSubmitCloudlets(brokerModern);
		createAndSubmitCloudlets(brokerAI);

		//Start simulation
		simulation.start();

		//Export Results
		List<Cloudlet> finalResults = new ArrayList<>();
		finalResults.addAll(brokerLegacy.getCloudletFinishedList());
		finalResults.addAll(brokerModern.getCloudletFinishedList());
		finalResults.addAll(brokerAI.getCloudletFinishedList());
		exportResultsToCSV(finalResults, "simulation_results.csv");
	}

	//Create tasks
	private static void createAndSubmitCloudlets(DatacenterBroker broker) {
		List<Cloudlet> cloudletList = new ArrayList<>();

		int numberOfCloudlets = 10000;
		long taskLength = 50000;
		int pesNumber = 1;

		for (int i = 0; i < numberOfCloudlets; i++) {
			Cloudlet cloudlet = new CloudletSimple(taskLength, pesNumber).setFileSize(1024).setOutputSize(1024);
			cloudletList.add(cloudlet);
		}

		broker.submitCloudletList(cloudletList);
	}

	private static Datacenter createSingleHostLegacy(CloudSimPlus simulation) {
		List<Pe> peList = new ArrayList<>();
		//The DL360 G1 has 2 CPUs, 1 Core each = 2 PEs
		peList.add(new PeSimple(1000));
		peList.add(new PeSimple(1000));

		//Parameters: RAM (2048 MB), BW (10 Mbps), Storage (36000 MB)
		Host host = new HostSimple(2048, 10, 36000, peList);

		//292W Active, 204W Idle (70%)
		host.setPowerModel(new PowerModelHostSimple(292, 204));

		return new DatacenterSimple(simulation, List.of(host));
	}

	private static Datacenter createSingleHostModern(CloudSimPlus simulation) {
		List<Pe> peList = new ArrayList<>();
		//The DL360 Gen10 has 2 CPUs, 28 Cores each = 56 PEs
		for (int i = 0; i < 56; i++) {
			peList.add(new PeSimple(2500));
		}

		//Parameters: RAM (98304 MB), BW (4000 Mbps), Storage (340000 MB)
		Host host = new HostSimple(98304, 4000, 340000, peList);

		//451W Active, 49W Idle (10.8%)
		host.setPowerModel(new PowerModelHostSimple(451, 49));

		return new DatacenterSimple(simulation, List.of(host));
	}

	private static Datacenter createSingleHostAI(CloudSimPlus simulation) {
		List<Pe> peList = new ArrayList<>();
		//The DL380a Gen11 has dual 64-core Xeons = 128 PEs
		for (int i = 0; i < 128; i++) {
			peList.add(new PeSimple(15000));
		}

		//Parameters: RAM (3072000 MB), BW (4000 Mbps), Storage (122880000 MB)
		Host host = new HostSimple(3072000, 4000, 122880000, peList);

		//736W Active, 235W Idle (31.93%)
		host.setPowerModel(new PowerModelHostSimple(736, 235));

		return new DatacenterSimple(simulation, List.of(host));
	}

	private static void exportResultsToCSV(List<Cloudlet> finalResults, String fileName) {
		System.out.println("Exporting 30,000 data rows to CSV...");

		try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
			writer.println(
					"Cloudlet_ID,Data_Center,Status,Length_MI,Start_Time_sec,Finish_Time_sec,Execution_Time_sec");

			for (Cloudlet c : finalResults) {

				String dcName = "Unknown";
				if (c.getVm().getId() == 1)
					dcName = "Legacy_2000";
				else if (c.getVm().getId() == 2)
					dcName = "Modern_Gen10";
				else if (c.getVm().getId() == 3)
					dcName = "AI_Gen11";

				writer.printf("%d,%s,%s,%d,%.2f,%.2f,%.2f\n", c.getId(), dcName, c.getStatus().name(), c.getLength(),
						c.getStartTime(), c.getFinishTime(), (c.getFinishTime() - c.getStartTime()));
			}
			System.out.println("SUCCESS: " + finalResults.size() + " rows saved to " + fileName);

		} catch (IOException e) {
			System.err.println("Error writing to CSV: " + e.getMessage());
		}
	}
}