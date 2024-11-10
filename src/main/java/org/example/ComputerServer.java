package org.example;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSFileStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ComputerServer extends JFrame {
    private static final int PORT = 12345;
    private static ArrayList<Computer1> computer1s = new ArrayList<>();
    private JTextArea textArea;
    private DefaultTableModel tableModel;
    private JTable table;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private static final long NETWORK_UPDATE_INTERVAL = 2; // segundos
    private static final long SYSTEM_UPDATE_INTERVAL = 10; // segundos
    private long[] lastBytesRecv;
    private long[] lastBytesSent;
    private long lastUpdateTime;

    public ComputerServer() {
        setTitle("Servidor de Computadoras");
        setSize(800, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, BorderLayout.WEST);

        createAndShowTable();

        // Agregar información del servidor al inicio
        addServerInfo();

        // Iniciar el monitoreo de red
        startNetworkMonitoring();

        // Iniciar el monitoreo del sistema
        startSystemMonitoring();

        // Iniciar el servidor en un hilo separado
        new Thread(this::startServer).start();
        SystemInfo si = new SystemInfo();
        List<NetworkIF> networkIFs = si.getHardware().getNetworkIFs();
        lastBytesRecv = new long[networkIFs.size()];
        lastBytesSent = new long[networkIFs.size()];
        lastUpdateTime = System.currentTimeMillis();

    }

    private void startSystemMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            updateSystemInfo();
            SwingUtilities.invokeLater(this::updateTableWithSystemInfo);
        }, 0, SYSTEM_UPDATE_INTERVAL, TimeUnit.SECONDS);
    }

    private void updateSystemInfo() {
        SystemInfo si = new SystemInfo();
        CentralProcessor processor = si.getHardware().getProcessor();
        GlobalMemory memory = si.getHardware().getMemory();
        OperatingSystem os = si.getOperatingSystem();


// Obtener el uso de CPU de manera más precisa
        double cpuLoad = processor.getSystemCpuLoad(1000L); // Espera 1 segundo para obtener una medición precisa
        double freeCpuPercentage = 100.0 - (cpuLoad * 100.0);

        // Si por alguna razón no podemos obtener la carga (valor -1), asumimos 0% de uso
        if (cpuLoad < 0) {
            freeCpuPercentage = 100.0;
        }

        // Actualizar información del servidor
        for (Computer1 computer : computer1s) {
            if (computer.getType().equals("Servidor")) {
                computer.setFreeCpuPercentage(freeCpuPercentage);

                // Actualizar memoria libre
                double freeMemory = memory.getAvailable() / (1024 * 1024 * 1024.0);
                computer.setFreeMemory(freeMemory);

                // Actualizar espacio en disco
                List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
                for (OSFileStore fileStore : fileStores) {
                    double freeDiskSpace = fileStore.getUsableSpace() / (1024 * 1024 * 1024.0);
                    computer.setFreeDiskSpace(freeDiskSpace);
                    break;
                }

                // Actualizar velocidad del procesador
                double processorSpeed = processor.getMaxFreq() / 1_000_000_000.0;
                computer.setProcessorSpeed(processorSpeed);
                break;
            }
        }
    }


    private void startNetworkMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            updateNetworkStats();
            SwingUtilities.invokeLater(this::updateTableWithSystemInfo);
        }, 0, NETWORK_UPDATE_INTERVAL, TimeUnit.SECONDS);
    }

    private void updateNetworkStats() {
        SystemInfo si = new SystemInfo();
        List<NetworkIF> networkIFs = si.getHardware().getNetworkIFs();
        long currentTime = System.currentTimeMillis();
        double timeElapsed = (currentTime - lastUpdateTime) / 1000.0; // tiempo en segundos

        for (Computer1 computer : computer1s) {
            double maxBandwidth = 1000.0; // 1 Gbps como valor de referencia
            double totalUsedBandwidth = 0.0;

            for (int i = 0; i < networkIFs.size(); i++) {
                NetworkIF net = networkIFs.get(i);
                net.updateAttributes();

                // Calcular bytes transferidos desde la última actualización
                long bytesRecvDelta = net.getBytesRecv() - (lastBytesRecv[i] != 0 ? lastBytesRecv[i] : net.getBytesRecv());
                long bytesSentDelta = net.getBytesSent() - (lastBytesSent[i] != 0 ? lastBytesSent[i] : net.getBytesSent());

                // Actualizar valores para la próxima medición
                lastBytesRecv[i] = net.getBytesRecv();
                lastBytesSent[i] = net.getBytesSent();

                // Convertir bytes a bits y calcular la tasa de transferencia en Mbps
                double usedBandwidth = 0.0;
                if (timeElapsed > 0) {
                    usedBandwidth = ((bytesRecvDelta + bytesSentDelta) * 8.0) / (timeElapsed * 1_000_000);
                    totalUsedBandwidth += usedBandwidth;
                }

                // Si la interfaz reporta una velocidad válida, usarla como referencia
                if (net.getSpeed() > 0) {
                    maxBandwidth = Math.max(maxBandwidth, net.getSpeed() / 1_000_000.0);
                }
            }

            // Calcular el porcentaje de ancho de banda libre
            double usedPercentage = (totalUsedBandwidth / maxBandwidth) * 100;
            double freePercentage = Math.max(0, Math.min(100, 100 - usedPercentage));

            // Redondear a dos decimales
            freePercentage = Math.round(freePercentage * 100.0) / 100.0;

            computer.setFreeBandwidthPercentage(freePercentage);

            // Registrar los valores para depuración
            System.out.printf("Bandwidth Stats - Used: %.2f Mbps, Max: %.2f Mbps, Free: %.2f%%\n",
                    totalUsedBandwidth, maxBandwidth, freePercentage);
        }

        lastUpdateTime = currentTime;
    }

    private void addServerInfo() {
        // Recolectar información del servidor
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

        String operatingSystemVersion = os.getVersionInfo().getVersion();
        double freeMemory = memory.getAvailable() / (1024 * 1024 * 1024);
        double freeBandwidthPercentage = 100.0;

        // Crear objeto Computer1 para el servidor
        Computer1 serverComputer = new Computer1(
                processorModel,
                processorSpeed,
                coreCount,
                diskCapacity,
                operatingSystemVersion,
                freeMemory,
                freeDiskSpace,
                freeBandwidthPercentage,
                "Servidor"
        );

        serverComputer.setConnectionStatus("Activo");
        computer1s.add(serverComputer);
        updateTableWithSystemInfo();
    }

    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor iniciado en el puerto: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        Thread clientThread = new Thread(() -> {
            Computer1 clientComputer = null;
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            System.out.println("Cliente conectado desde: " + clientAddress);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                while (!clientSocket.isClosed()) {
                    String clientSystemInfo = in.readLine();
                    if (clientSystemInfo == null) {
                        break; // Cliente desconectado
                    }

                    clientComputer = parseClientSystemInfo(clientSystemInfo);
                    clientComputer.setType("Cliente");
                    clientComputer.setConnectionStatus("Conectado");

                    // Actualizar o agregar cliente
                    synchronized (computer1s) {
                        boolean updated = false;
                        for (int i = 0; i < computer1s.size(); i++) {
                            Computer1 existingComputer = computer1s.get(i);
                            if (existingComputer.getType().equals("Cliente") &&
                                    existingComputer.getProcessorModel().equals(clientComputer.getProcessorModel())) {
                                // Actualizar datos del cliente existente
                                computer1s.set(i, clientComputer);
                                updated = true;
                                break;
                            }
                        }
                        if (!updated) {
                            computer1s.add(clientComputer);
                        }
                    }

                    // Actualizar la tabla
                    SwingUtilities.invokeLater(this::updateTableWithSystemInfo);

                    // Confirmar recepción al cliente
                    out.println("Datos actualizados correctamente");
                }

            } catch (IOException e) {
                System.err.println("Error en la comunicación con el cliente: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar el socket del cliente: " + e.getMessage());
                }

                // Manejar la desconexión del cliente
                synchronized (computer1s) {
                    if (clientComputer != null) {
                        final Computer1 finalClientComputer = clientComputer;
                        computer1s.stream()
                                .filter(c -> c.getType().equals("Cliente") &&
                                        c.getProcessorModel().equals(finalClientComputer.getProcessorModel()))
                                .forEach(c -> c.setConnectionStatus("Desconectado"));
                    }
                }
                System.out.println("Cliente desconectado: " + clientAddress);
                SwingUtilities.invokeLater(this::updateTableWithSystemInfo);
            }
        });
        clientThread.start();
    }

    private void updateClientInList(Computer1 newClient) {
        boolean updated = false;
        for (int i = 0; i < computer1s.size(); i++) {
            Computer1 existingComputer = computer1s.get(i);
            if (existingComputer.getType().equals("Cliente") &&
                    existingComputer.getProcessorModel().equals(newClient.getProcessorModel())) {
                computer1s.set(i, newClient);
                updated = true;
                break;
            }
        }
        if (!updated) {
            computer1s.add(newClient);
        }
    }

    // Nuevo método para manejar la desconexión del cliente
    private void handleClientDisconnection(Computer1 clientComputer, String clientAddress) {
        synchronized (computer1s) {
            if (clientComputer != null) {
                // Buscar y actualizar el cliente por modelo de procesador
                computer1s.stream()
                        .filter(c -> c.getType().equals("Cliente") &&
                                c.getProcessorModel().equals(clientComputer.getProcessorModel()))
                        .forEach(c -> c.setConnectionStatus("Desconectado"));
            } else {
                // Si no tenemos información del cliente, buscar por cualquier cliente conectado
                computer1s.stream()
                        .filter(c -> c.getType().equals("Cliente") &&
                                c.getConnectionStatus().equals("Conectado"))
                        .forEach(c -> c.setConnectionStatus("Desconectado"));
            }
        }

        System.out.println("Cliente desconectado: " + clientAddress);
        SwingUtilities.invokeLater(this::updateTableWithSystemInfo);
    }

    private void updateTableWithSystemInfo() {
        // Ordenar la lista por memoria libre (de mayor a menor)
        synchronized (computer1s) {
            ArrayList<Computer1> sortedComputers = new ArrayList<>(computer1s);
            sortedComputers.sort(Comparator.comparingDouble(Computer1::getFreeMemory).reversed());

            tableModel.setRowCount(0);
            int rank = 1;
            for (Computer1 computer : sortedComputers) {
                Object[] rowData = {
                        rank++,
                        computer.getType(),
                        computer.getProcessorModel(),
                        String.format("%.2f", computer.getProcessorSpeed()),
                        computer.getCoreCount(),
                        computer.getDiskCapacity(),
                        computer.getOperatingSystemVersion(),
                        String.format("%.2f", computer.getFreeMemory()),
                        String.format("%.2f", computer.getFreeDiskSpace()),
                        String.format("%.1f", computer.getFreeCpuPercentage()),       // CPU Libre
                        String.format("%.1f", computer.getFreeBandwidthPercentage()), // Ancho de Banda Libre
                        computer.getConnectionStatus()                                 // Estado de Conexión
                };
                tableModel.addRow(rowData);
            }
        }
    }

    private Computer1 parseClientSystemInfo(String jsonData) {
        try {
            // Eliminar las llaves del inicio y final
            jsonData = jsonData.substring(1, jsonData.length() - 1);

            // Dividir por comas y crear un mapa de valores
            String[] pairs = jsonData.split(",");
            java.util.Map<String, String> values = new java.util.HashMap<>();

            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                String key = keyValue[0].replace("\"", "").trim();
                String value = keyValue[1].replace("\"", "").trim();
                values.put(key, value);
            }

            return new Computer1(
                    values.get("processorModel"),
                    Double.parseDouble(values.get("processorSpeed")),
                    Integer.parseInt(values.get("coreCount")),
                    Long.parseLong(values.get("diskCapacity")),
                    values.get("operatingSystemVersion"),
                    Double.parseDouble(values.get("freeMemory")),
                    Double.parseDouble(values.get("freeDiskSpace")),
                    Double.parseDouble(values.get("freeBandwidthPercentage")),
                    "Cliente"
            );
        } catch (Exception e) {
            System.err.println("Error al parsear datos del cliente: " + e.getMessage());
            throw new RuntimeException("Error al parsear datos del cliente", e);
        }
    }

    private void updateTextArea(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
        });
    }

    private void createAndShowTable() {
        String[] columnNames = {
                "Ranking",
                "Tipo",
                "Modelo del Procesador",
                "Velocidad (GHz)",
                "Núcleos",
                "Capacidad (GB)",
                "Versión del SO",
                "Memoria Libre (GB)",
                "Espacio Libre (GB)",
                "CPU Libre (%)", // Nueva columna
                "Ancho de Banda Libre (%)",
                "Estado de Conexión"
        };

        tableModel = new DefaultTableModel(columnNames, 0);
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        setVisible(true);
    }

    private Computer1 gatherClientSystemInfo() {
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

        String operatingSystemVersion = os.getVersionInfo().getVersion();
        double freeMemory = memory.getAvailable() / (1024 * 1024 * 1024);
        double freeBandwidthPercentage = 100.0;

        return new Computer1(
                processorModel,
                processorSpeed,
                coreCount,
                diskCapacity,
                operatingSystemVersion,
                freeMemory,
                freeDiskSpace,
                freeBandwidthPercentage,
                "Cliente"
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ComputerServer::new);
    }
}