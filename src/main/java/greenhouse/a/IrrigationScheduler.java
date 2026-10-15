package greenhouse.a;

/**
 * Сервис A: резервирует слот полива, подтверждает его и освобождает (откат шага A).
 *
 * Ошибки:
 *  - IllegalArgumentException — плохой ввод (формат грядки, неизвестный слот);
 *  - IllegalStateException    — правило предметной области не позволяет действие
 *                               (нет показаний, полив не нужен, грядка уже занята, неверный переход).
 */
public class IrrigationScheduler {

    private final SlotRepository repository;
    private final SoilSensorArray sensors;
    private final IrrigationPolicy policy;

    public IrrigationScheduler(SlotRepository repository, SoilSensorArray sensors, IrrigationPolicy policy) {
        this.repository = repository;
        this.sensors = sensors;
        this.policy = policy;
    }

    public IrrigationSlot reserve(String bedId) {
        SoilSensorArray.requireValidBedId(bedId);

        double moisture = sensors.latestMoisture(bedId)
                .orElseThrow(() -> new IllegalStateException("No soil readings for bed " + bedId));

        if (!policy.needsIrrigation(moisture)) {
            throw new IllegalStateException("Bed " + bedId + " does not need irrigation (moisture "
                    + moisture + "%)");
        }
        if (repository.findActiveByBed(bedId).isPresent()) {
            throw new IllegalStateException("Bed " + bedId + " already has a reserved irrigation slot");
        }

        IrrigationSlot slot = IrrigationSlot.reserve(bedId, policy.litersFor(moisture));
        repository.save(slot);
        return slot;
    }

    public IrrigationSlot confirm(long slotId) {
        IrrigationSlot slot = load(slotId);
        slot.confirm();
        repository.save(slot);
        return slot;
    }

    /**
     * Откат A. Идемпотентен: повторный вызов для уже освобождённого слота ничего не меняет —
     * это нужно, чтобы фасад мог безопасно доводить откат после рестарта.
     */
    public IrrigationSlot release(long slotId) {
        IrrigationSlot slot = load(slotId);
        if (slot.getStatus() == IrrigationSlot.Status.RELEASED) {
            return slot;
        }
        slot.release();
        repository.save(slot);
        return slot;
    }

    private IrrigationSlot load(long slotId) {
        return repository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown slot " + slotId));
    }
}