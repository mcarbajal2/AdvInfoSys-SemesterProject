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
 * A CloudSim Plus simulation comparing Multi-Host Data 
 * Center Clusters to analyze power and efficiency trade-offs at scale.
 */
public class MultiHost {

    //Scale factor
    private static final int NUMBER_OF_HOSTS = 100; 

    public static void main(String[] args) {
        Log.setLevel(Level.ERROR);
        
        CloudSimPlus simulation = new CloudSimPlus();

        createMultiHostLegacy(simulation, NUMBER_OF_HOSTS);
        createMultiHostModern(simulation, NUMBER_OF_HOSTS);
        createMultiHostAI(simulation, NUMBER_OF_HOSTS);
        
        DatacenterBroker brokerLegacy = new DatacenterBrokerSimple(simulation);
        DatacenterBroker brokerModern = new DatacenterBrokerSimple(simulation);
        DatacenterBroker brokerAI = new DatacenterBrokerSimple(simulation);

        List<Vm> legacyVms = new ArrayList<>();
        List<Vm> modernVms = new ArrayList<>();
        List<Vm> aiVms = new ArrayList<>();

        for (int i = 0; i < NUMBER_OF_HOSTS; i++) {
            //Legacy VMs (IDs 1000+)
            Vm vmLegacy = new VmSimple(1000, 2)
                                .setRam(2048).setBw(10).setSize(36000)
                                .setCloudletScheduler(new CloudletSchedulerSpaceShared()); 
            vmLegacy.setId(1000 + i);
            legacyVms.add(vmLegacy);
            
            //Modern VMs (IDs 2000+)
            Vm vmModern = new VmSimple(2500, 56)
                                .setRam(98304).setBw(4000).setSize(340000)
                                .setCloudletScheduler(new CloudletSchedulerSpaceShared()); 
            vmModern.setId(2000 + i);
            modernVms.add(vmModern);
            
            //AI VMs (IDs 3000+)
            Vm vmAI = new VmSimple(15000, 128)
                                .setRam(3072000).setBw(4000).setSize(122880000)
                                .setCloudletScheduler(new CloudletSchedulerSpaceShared());    
            vmAI.setId(3000 + i);
            aiVms.add(vmAI);
        }
        
        brokerLegacy.submitVmList(legacyVms);
        brokerModern.submitVmList(modernVms);
        brokerAI.submitVmList(aiVms);

        createAndSubmitCloudlets(brokerLegacy, NUMBER_OF_HOSTS);
        createAndSubmitCloudlets(brokerModern, NUMBER_OF_HOSTS);
        createAndSubmitCloudlets(brokerAI, NUMBER_OF_HOSTS);
        simulation.start();

        //Export results
        List<Cloudlet> finalResults = new ArrayList<>();
        finalResults.addAll(brokerLegacy.getCloudletFinishedList());
        finalResults.addAll(brokerModern.getCloudletFinishedList());
        finalResults.addAll(brokerAI.getCloudletFinishedList());
        exportResultsToCSV(finalResults, "multi_simulation_results.csv");
    }

    //Scales the workload based on number of hosts
    private static void createAndSubmitCloudlets(DatacenterBroker broker, int numHosts) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        
        //10,000 tasks per host
        int numberOfCloudlets = 10000 * numHosts; 
        long taskLength = 50000; 
        int pesNumber = 1;       

        for (int i = 0; i < numberOfCloudlets; i++) {
            Cloudlet cloudlet = new CloudletSimple(taskLength, pesNumber)
                                    .setFileSize(1024)
                                    .setOutputSize(1024);
            cloudletList.add(cloudlet);
        }
        broker.submitCloudletList(cloudletList);
    }

    private static Datacenter createMultiHostLegacy(CloudSimPlus simulation, int numHosts) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new PeSimple(1000)); 
            peList.add(new PeSimple(1000)); 
            Host host = new HostSimple(2048, 10, 36000, peList);
            host.setPowerModel(new PowerModelHostSimple(292, 204));
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private static Datacenter createMultiHostModern(CloudSimPlus simulation, int numHosts) {
        List<Host> hostList = new ArrayList<>();
        for (int h = 0; h < numHosts; h++) {
            List<Pe> peList = new ArrayList<>();
            for(int i = 0; i < 56; i++) {
                peList.add(new PeSimple(2500)); 
            }
            Host host = new HostSimple(98304, 4000, 340000, peList);
            host.setPowerModel(new PowerModelHostSimple(451, 49));
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private static Datacenter createMultiHostAI(CloudSimPlus simulation, int numHosts) {
        List<Host> hostList = new ArrayList<>();
        for (int h = 0; h < numHosts; h++) {
            List<Pe> peList = new ArrayList<>();
            for(int i = 0; i < 128; i++) {
                peList.add(new PeSimple(15000));
            }
            Host host = new HostSimple(3072000, 4000, 122880000, peList);
            host.setPowerModel(new PowerModelHostSimple(736, 235));
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private static void exportResultsToCSV(List<Cloudlet> finalResults, String fileName) {
        System.out.println("Exporting " + finalResults.size() + " data rows to CSV...");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("Cloudlet_ID,Data_Center,VM_ID,Status,Length_MI,Start_Time_sec,Finish_Time_sec,Execution_Time_sec");
            
            for (Cloudlet c : finalResults) {
                
                String dcName = "Unknown";
                long vmId = c.getVm().getId();
                
                if (vmId >= 3000) dcName = "AI_Gen11_Cluster";
                else if (vmId >= 2000) dcName = "Modern_Gen10_Cluster";
                else if (vmId >= 1000) dcName = "Legacy_2000_Cluster";

                writer.printf("%d,%s,%d,%s,%d,%.2f,%.2f,%.2f\n",
                        c.getId(),
                        dcName,
                        vmId,
                        c.getStatus().name(),
                        c.getLength(),
                        c.getStartTime(), 
                        c.getFinishTime(),
                        (c.getFinishTime() - c.getStartTime())); 
            }
            System.out.println("SUCCESS: File saved as " + fileName);
            
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }
}