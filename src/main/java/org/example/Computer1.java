package org.example;

class Computer1 {
    private String processorModel;
    private double processorSpeed;
    private int coreCount;
    private long diskCapacity;
    private String operatingSystemVersion;
    private double freeMemory;
    private double freeDiskSpace;
    private double freeBandwidthPercentage;
    private String connectionStatus;
    private String type;
    private double freeCpuPercentage; // Nuevo campo

    public Computer1(String processorModel, double processorSpeed, int coreCount,
                     long diskCapacity, String operatingSystemVersion, double freeMemory,
                     double freeDiskSpace, double freeBandwidthPercentage, String type) {
        this.processorModel = processorModel;
        this.processorSpeed = processorSpeed;
        this.coreCount = coreCount;
        this.diskCapacity = diskCapacity;
        this.operatingSystemVersion = operatingSystemVersion;
        this.freeMemory = freeMemory;
        this.freeDiskSpace = freeDiskSpace;
        this.freeBandwidthPercentage = freeBandwidthPercentage;
        this.freeCpuPercentage = 100.0; // Valor inicial
        this.type = type;
        this.connectionStatus = "Desconectado";
    }

    public String getType() {
        return type;
    }

    // Agregar setters para actualización de datos
    public void setFreeMemory(double freeMemory) {
        this.freeMemory = freeMemory;
    }

    public void setFreeDiskSpace(double freeDiskSpace) {
        this.freeDiskSpace = freeDiskSpace;
    }

    public void setProcessorSpeed(double processorSpeed) {
        this.processorSpeed = processorSpeed;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setConnectionStatus(String status) {
        this.connectionStatus = status;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public String getProcessorModel() {
        return processorModel;
    }

    public double getProcessorSpeed() {
        return processorSpeed;
    }

    public int getCoreCount() {
        return coreCount;
    }

    public long getDiskCapacity() {
        return diskCapacity;
    }

    public String getOperatingSystemVersion() {
        return operatingSystemVersion;
    }

    public double getFreeMemory() {
        return freeMemory;
    }

    public double getFreeDiskSpace() {
        return freeDiskSpace;
    }

    public double getFreeBandwidthPercentage() {
        return freeBandwidthPercentage;
    }

    public double getFreeCpuPercentage() {
        return freeCpuPercentage;
    }
    public void setFreeCpuPercentage(double percentage) {
        this.freeCpuPercentage = percentage;
    }
    public void setFreeBandwidthPercentage(double percentage) {
        this.freeBandwidthPercentage = percentage;
    }
}