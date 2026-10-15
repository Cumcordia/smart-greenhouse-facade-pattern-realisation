package greenhouse.c;

public class DosageRecord {

    public enum Status { PLANNED, DISPENSED, REVERTED }

    private Long id;
    private final String bedId;
    private final DosageCalculator.Crop crop;
    private final double waterLiters;
    private final double grams;
    private Status status;

    private DosageRecord(Long id, String bedId, DosageCalculator.Crop crop,
                         double waterLiters, double grams, Status status) {
        this.id = id;
        this.bedId = bedId;
        this.crop = crop;
        this.waterLiters = waterLiters;
        this.grams = grams;
        this.status = status;
    }

    public static DosageRecord plan(String bedId, DosageCalculator.Crop crop, double waterLiters, double grams) {
        if (bedId == null || bedId.isBlank()) {
            throw new IllegalArgumentException("bedId must not be blank");
        }
        if (crop == null) {
            throw new IllegalArgumentException("crop must not be null");
        }
        if (waterLiters <= 0 || grams <= 0) {
            throw new IllegalArgumentException("waterLiters and grams must be positive");
        }
        return new DosageRecord(null, bedId, crop, waterLiters, grams, Status.PLANNED);
    }

    static DosageRecord restore(long id, String bedId, DosageCalculator.Crop crop,
                                double waterLiters, double grams, Status status) {
        return new DosageRecord(id, bedId, crop, waterLiters, grams, status);
    }

    public void dispense() {
        if (status != Status.PLANNED) {
            throw new IllegalStateException("Cannot dispense dosage in status " + status);
        }
        status = Status.DISPENSED;
    }

    public void revert() {
        if (status == Status.REVERTED) {
            throw new IllegalStateException("Dosage is already REVERTED");
        }
        status = Status.REVERTED;
    }

    void assignId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String getBedId() { return bedId; }
    public DosageCalculator.Crop getCrop() { return crop; }
    public double getWaterLiters() { return waterLiters; }
    public double getGrams() { return grams; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "DosageRecord{id=" + id + ", bed=" + bedId + ", crop=" + crop
                + ", water=" + waterLiters + " L, grams=" + grams + ", status=" + status + "}";
    }
}
