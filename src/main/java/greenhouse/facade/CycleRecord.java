package greenhouse.facade;

public class CycleRecord {

    public enum Status { PENDING_B, PENDING_C, COMPLETED, COMPENSATED }

    private Long id;
    private final String bedId;
    private final String crop;
    private final double temperatureC;
    private final double humidityPercent;
    private Status status;
    private final long slotId;
    private Long runId;
    private Long dosageId;
    private Long alertId;
    private String failureReason;

    private CycleRecord(Long id, String bedId, String crop, double temperatureC, double humidityPercent,
                        Status status, long slotId, Long runId, Long dosageId, Long alertId,
                        String failureReason) {
        this.id = id;
        this.bedId = bedId;
        this.crop = crop;
        this.temperatureC = temperatureC;
        this.humidityPercent = humidityPercent;
        this.status = status;
        this.slotId = slotId;
        this.runId = runId;
        this.dosageId = dosageId;
        this.alertId = alertId;
        this.failureReason = failureReason;
    }

    public static CycleRecord begin(String bedId, String crop, double temperatureC,
                                    double humidityPercent, long slotId) {
        if (bedId == null || bedId.isBlank()) {
            throw new IllegalArgumentException("bedId must not be blank");
        }
        if (crop == null || crop.isBlank()) {
            throw new IllegalArgumentException("crop must not be blank");
        }
        return new CycleRecord(null, bedId, crop, temperatureC, humidityPercent,
                Status.PENDING_B, slotId, null, null, null, null);
    }

    static CycleRecord restore(long id, String bedId, String crop, double temperatureC,
                               double humidityPercent, Status status, long slotId, Long runId,
                               Long dosageId, Long alertId, String failureReason) {
        return new CycleRecord(id, bedId, crop, temperatureC, humidityPercent, status, slotId,
                runId, dosageId, alertId, failureReason);
    }

    public void attachRun(long runId) {
        requireStatus(Status.PENDING_B, "attach a run");
        this.runId = runId;
    }

    public void advanceToPendingC() {
        requireStatus(Status.PENDING_B, "advance to PENDING_C");
        status = Status.PENDING_C;
    }

    public void attachDosage(long dosageId) {
        requireStatus(Status.PENDING_C, "attach a dosage");
        this.dosageId = dosageId;
    }

    public void attachAlert(long alertId) {
        requireStatus(Status.PENDING_C, "attach an alert");
        this.alertId = alertId;
    }

    public void complete() {
        requireStatus(Status.PENDING_C, "complete");
        status = Status.COMPLETED;
    }

    public void compensate(String reason) {
        if (!isInFlight()) {
            throw new IllegalStateException("Cannot compensate cycle in status " + status);
        }
        status = Status.COMPENSATED;
        failureReason = reason;
    }

    public ChainStep farthestStep() {
        if (dosageId != null || alertId != null) {
            return ChainStep.C;
        }
        if (runId != null || status == Status.PENDING_C) {
            return ChainStep.B;
        }
        return ChainStep.A;
    }

    public boolean isInFlight() {
        return status == Status.PENDING_B || status == Status.PENDING_C;
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " while cycle is " + status);
        }
    }

    void assignId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String getBedId() { return bedId; }
    public String getCrop() { return crop; }
    public double getTemperatureC() { return temperatureC; }
    public double getHumidityPercent() { return humidityPercent; }
    public Status getStatus() { return status; }
    public long getSlotId() { return slotId; }
    public Long getRunId() { return runId; }
    public Long getDosageId() { return dosageId; }
    public Long getAlertId() { return alertId; }
    public String getFailureReason() { return failureReason; }

    @Override
    public String toString() {
        return "CycleRecord{id=" + id + ", bed=" + bedId + ", crop=" + crop + ", status=" + status
                + ", slot=" + slotId + ", run=" + runId + ", dosage=" + dosageId + ", alert=" + alertId
                + (failureReason == null ? "" : ", reason=" + failureReason) + "}";
    }
}
