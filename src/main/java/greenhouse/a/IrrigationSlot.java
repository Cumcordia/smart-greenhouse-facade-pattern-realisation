package greenhouse.a;

/**
 * Сущность A: слот полива для грядки. Сама следит за допустимыми переходами статуса,
 * независимо от того, кто её вызывает (фасад, тест или main).
 *
 *   RESERVED --> CONFIRMED --> RELEASED
 *   RESERVED ----------------> RELEASED
 */
public class IrrigationSlot {

    public enum Status { RESERVED, CONFIRMED, RELEASED }

    private Long id;               // null, пока слот не сохранён в БД
    private final String bedId;
    private final double liters;
    private Status status;

    private IrrigationSlot(Long id, String bedId, double liters, Status status) {
        this.id = id;
        this.bedId = bedId;
        this.liters = liters;
        this.status = status;
    }

    /** Новый слот всегда начинает со статуса RESERVED. */
    public static IrrigationSlot reserve(String bedId, double liters) {
        if (bedId == null || bedId.isBlank()) {
            throw new IllegalArgumentException("bedId must not be blank");
        }
        if (liters <= 0) {
            throw new IllegalArgumentException("liters must be positive");
        }
        return new IrrigationSlot(null, bedId, liters, Status.RESERVED);
    }

    /** Восстановление из хранилища — переходы не проверяются, это уже сохранённое состояние. */
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

    /**
     * Активный слот занимает грядку, пока цикл идёт (RESERVED). Подтверждённый слот — это уже
     * завершённый успешный цикл, он грядку не блокирует и следующий цикл возможен.
     */
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
