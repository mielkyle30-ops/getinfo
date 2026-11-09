import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class CDPParser {

    public void parsing() {
        parsing("./cisco/" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "/"); // default folder
    }

    /** Parse every *show_cdp_neighbors_detail* file in folderPath (the run folder) into one CSV. */
    public void parsing(String folderPath) {
        if (!folderPath.endsWith("/") && !folderPath.endsWith(File.separator)) folderPath += File.separator;
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles((dir, name) -> name.contains("show_cdp_neighbors_detail"));

        List<DeviceInfo> deviceInfoList = new ArrayList<>();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    String hostname = null;
                    String deviceId = null, ipAddress = null, platform = null, interfaceInfo = null, portId = null;

                    List<String> lines = new ArrayList<>();
                    while ((line = br.readLine()) != null) {
                        lines.add(line.trim());
                    }

                    // extract hostname
                    if (!lines.isEmpty()) {
                        hostname = lines.get(lines.size() - 1);
                        if (hostname.endsWith("#")) {
                            hostname = hostname.substring(0, hostname.length() - 1);
                        }
                    }

                    for (String ln : lines) {
                        if (ln.startsWith("Device ID:")) {
                            deviceId = ln.split(":")[1].trim();
                        } else if (ln.startsWith("IP address:")) {
                            ipAddress = ln.split(":")[1].trim();
                        } else if (ln.startsWith("Platform:")) {
                            platform = ln.split(":")[1].trim().split(",")[0];
                        } else if (ln.startsWith("Interface:")) {
                            String[] parts = ln.split(",");
                            interfaceInfo = parts[0].split(":")[1].trim();
                            portId = parts[1].split(":")[1].trim();
                        } else if (ln.equals("-------------------------")) {
                            if (deviceId != null && ipAddress != null && platform != null && interfaceInfo != null
                                    && portId != null) {
                                deviceInfoList.add(
                                        new DeviceInfo(hostname, deviceId, ipAddress, platform, interfaceInfo, portId));
                            }
                            deviceId = ipAddress = platform = interfaceInfo = portId = null; // reset for next device
                        }
                    }
                    // add the last device
                    if (deviceId != null && ipAddress != null && platform != null && interfaceInfo != null
                            && portId != null) {
                        deviceInfoList
                                .add(new DeviceInfo(hostname, deviceId, ipAddress, platform, interfaceInfo, portId));
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // sort by hostname, descending
            deviceInfoList.sort(Comparator.comparing((DeviceInfo d) -> d.hostname));

            // write CSV
            File csvFile = new File(folderPath + date + "_CDP_detail.csv");
            if (csvFile.exists()) {
                csvFile.delete(); // remove old file
            }

            try (FileWriter writer = new FileWriter(csvFile)) {
                writer.append("hostname,deviceId,ipAddress,platform,interfaceInfo,portId\n");
                for (DeviceInfo device : deviceInfoList) {
                    writer.append(device.toString()).append("\n");
                }
                System.out.println("Data has been written to CDP_detail.csv");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No files found containing 'show_cdp_neighbors_detail'");
        }
    }
}