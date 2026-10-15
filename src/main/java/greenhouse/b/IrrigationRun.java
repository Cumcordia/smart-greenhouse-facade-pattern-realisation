package greenhouse.b;

/**
 * Сущность B: один запуск полива. Сама следит за допустимыми переходами статуса.
 *
 *   STARTED --> COMPLETED
 *   STARTED --> ABORTED
 *
 * COMPLETED и ABORTED — конечные состояния.
 */
public class IrrigationRun {

    public enum Status { STARTED, COMPLETED, ABORTED }

    private Long id;               // null, пока запуск не сохранён в БД
    private final String bedId;
    private final double liters;
    private Status status;

    private IrrigationRun(Long id, String bedId, double liters, Status status) {
        this.id = id;
        this.bedId = bedId;
        this.liters = liters;
        this.status = status;
    }

    /** Новый запуск всегда начинается со статуса STARTED. */
    public static IrrigationRun start(String bedId, double liters) {
        if (bedId == null || bedId.isBlank()) {
            throw new IllegalArgumentException("bedId must not be blank");
        }
        if (liters <= 0) {
            throw new IllegalArgumentException("liters must be positive");
        }
        return new IrrigationRun(null, bedId, liters, Status.STARTED);
    }

    /** Восстановление из хранилища — это уже сохранённое состояние, переходы не проверяются. */
    static IrrigationRun restore(long id, String bedId, double liters, Status status) {
        return new IrrigationRun(id, bedId, liters, status);
    }

    public void complete() {
        if (status != Status.STARTED) {
            throw new IllegalStateException("Cannot complete run in status " + status);
        }
        status = Status.COMPLETED;
    }

    public void abort() {
        if (status != Status.STARTED) {
            throw new IllegalStateException("Cannot abort run in status " + status);
        }
        status = Status.ABORTED;
    }

    /** Активный запуск занимает грядку: поливается прямо сейчас. */
    public boolean isActive() {
        return status == Status.STARTED;
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
        return "IrrigationRun{id=" + id + ", bed=" + bedId + ", liters=" + liters + ", status=" + status + "}";
    }
}
