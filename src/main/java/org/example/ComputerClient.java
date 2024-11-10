package org.example;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSFileStore;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ComputerClient {
    private static final String SERVER_ADDRESS = "25.54.76.182";
    private static final int SERVER_PORT = 12345;
    private static final long UPDATE_INTERVAL = 10; // segundos
    private static PrintWriter out;
    private static BufferedReader in;
    private static Socket socket;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        try {
            connectToServer();
            startPeriodicUpdates();

            // Mantener la aplicación ejecutándose
            while (true) {
                try {
                    String serverResponse = in.readLine();
                    if (serverResponse == null) {
                        System.out.println("Conexión perdida. Intentando reconectar...");
                        reconnect();
                    } else {
                        System.out.println("Respuesta del servidor: " + serverResponse);
                    }
                } catch (IOException e) {
                    System.out.println("Error en la conexión. Intentando reconectar...");
                    reconnect();
                }
                Thread.sleep(1000); // Pequeña pausa para no saturar el CPU
            }
        } catch (Exception e) {
            System.err.println("Error fatal: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private static void connectToServer() throws IOException {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println("Conectado al servidor");
    }

    private static void reconnect() {
        cleanup();
        try {
            Thread.sleep(5000); // Esperar 5 segundos antes de reconectar
            connectToServer();
        } catch (Exception e) {
            System.err.println("Error al reconectar: " + e.getMessage());
        }
    }

    private static void cleanup() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar conexiones: " + e.getMessage());
        }
    }

    private static void startPeriodicUpdates() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendSystemInfo();
            } catch (Exception e) {
                System.err.println("Error al enviar actualización: " + e.getMessage());
                reconnect();
            }
        }, 0, UPDATE_INTERVAL, TimeUnit.SECONDS);
    }

    private static void sendSystemInfo() {
        try {
            SystemInfo systemInfo = new SystemInfo();
            CentralProcessor processor = systemInfo.getHardware().getProcessor();
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            OperatingSystem os = systemInfo.getOperatingSystem();

            String processorModel = processor.getProcessorIdentifier().getName();
            double processorSpeed = processor.getMaxFreq() / 1_000_000_000.0;
            int coreCount = processor.getLogicalProcessorCount();

            List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
            long diskCapacity = 0;
            double freeDiskSpace = 0;

            if (!diskStores.isEmpty()) {
                diskCapacity = diskStores.get(0).getSize() / (1024 * 1024 * 1024);
                List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
                for (OSFileStore fileStore : fileStores) {
                    freeDiskSpace = fileStore.getUsableSpace() / (1024 * 1024 * 1024);
                    break;
                }
            }

            // Obtener el uso de CPU de manera más precisa
            double cpuLoad = processor.getSystemCpuLoad(1000L); // Espera 1 segundo para obtener una medición precisa
            double freeCpuPercentage = 100.0 - (cpuLoad * 100.0);

            // Si por alguna razón no podemos obtener la carga (valor -1), asumimos 0% de uso
            if (cpuLoad < 0) {
                freeCpuPercentage = 100.0;
            }

            String operatingSystemVersion = os.getVersionInfo().getVersion();
            double freeMemory = memory.getAvailable() / (1024 * 1024 * 1024);
            double freeBandwidthPercentage = calculateFreeBandwidth();

            String systemData = String.format(
                    "{\"processorModel\":\"%s\"," +
                            "\"processorSpeed\":%.2f," +
                            "\"coreCount\":%d," +
                            "\"diskCapacity\":%d," +
                            "\"operatingSystemVersion\":\"%s\"," +
                            "\"freeMemory\":%.2f," +
                            "\"freeDiskSpace\":%.2f," +
                            "\"freeBandwidthPercentage\":%.2f," +
                            "\"freeCpuPercentage\":%.2f}",
                    processorModel, processorSpeed, coreCount, diskCapacity,
                    operatingSystemVersion, freeMemory, freeDiskSpace,
                    freeBandwidthPercentage, freeCpuPercentage
            );

            out.println(systemData);
            System.out.println("Información del sistema enviada");
        } catch (Exception e) {
            throw new RuntimeException("Error al recopilar o enviar información del sistema", e);
        }
    }


    private static double calculateFreeBandwidth() {
        try {
            SystemInfo si = new SystemInfo();
            List<NetworkIF> networkIFs = si.getHardware().getNetworkIFs();
            double maxBandwidth = 1000.0; // 1 Gbps como valor de referencia
            double totalUsedBandwidth = 0.0;

            for (NetworkIF net : networkIFs) {
                net.updateAttributes();

                // Si la interfaz reporta una velocidad válida, usarla como referencia
                if (net.getSpeed() > 0) {
                    maxBandwidth = Math.max(maxBandwidth, net.getSpeed() / 1_000_000.0);
                }

                // Calcular el uso actual basado en las tasas de transferencia
                double currentBandwidth = (net.getBytesRecv() + net.getBytesSent()) * 8.0 / 1_000_000;
                totalUsedBandwidth += currentBandwidth;
            }

            double usedPercentage = (totalUsedBandwidth / maxBandwidth) * 100;
            return Math.max(0, Math.min(100, 100 - usedPercentage));
        } catch (Exception e) {
            System.err.println("Error al calcular el ancho de banda: " + e.getMessage());
            return 0.0;
        }
    }
}