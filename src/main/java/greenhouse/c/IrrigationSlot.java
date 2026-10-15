package greenhouse.c;

public class IrrigationSlot {

    public enum Status { RESERVED, CONFIRMED, RELEASED }

    private Long id;
    private final String bedId;
    private final double liters;
    private Status status;

    private IrrigationSlot(Long id, String bedId, double liters, Status status) {
        this.id = id;
        this.bedId = bedId;
        this.liters = liters;
        this.status = status;
    }

    public static IrrigationSlot reserve(String bedId, double liters) {
        if (bedId == null || bedId.isBlank()) {
            throw new IllegalArgumentException("bedId must not be blank");
        }
        if (liters <= 0) {
            throw new IllegalArgumentException("liters must be positive");
        }
        return new IrrigationSlot(null, bedId, liters, Status.RESERVED);
    }

    static IrrigationSlot restore(long id, String bedId, double liters, Status status) {
        return new IrrigationSlot(id, bedId, liters, status);
    }

    public void confirm() {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Cannot confirm slot in status " + status);
        }
        status = Status.CONFIRMED;
    }

    public void release() {
        if (status == Status.RELEASED) {
            throw new IllegalStateException("Slot is already RELEASED");
        }
        status = Status.RELEASED;
    }

    public boolean isActive() {
        return status == Status.RESERVED;
    }

    void assignId(long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String getBedId() { return bedId; }
    public double getLiters() { return liters; }
    public Status getStatus() { return status; }

    @Override
    public String toString() {
        return "IrrigationSlot{id=" + id + ", bed=" + bedId + ", liters=" + liters + ", status=" + status + "}";
    }
}
