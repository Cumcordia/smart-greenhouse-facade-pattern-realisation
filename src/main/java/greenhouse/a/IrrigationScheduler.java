package greenhouse.a;

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