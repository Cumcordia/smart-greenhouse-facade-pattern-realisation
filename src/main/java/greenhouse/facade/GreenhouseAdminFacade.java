package greenhouse.facade;

import greenhouse.a.IrrigationSlot;
import greenhouse.a.SlotRepository;
import greenhouse.b.IrrigationController;
import greenhouse.b.IrrigationRun;
import greenhouse.b.RunRepository;
import greenhouse.b.VentilationPolicy;
import greenhouse.c.AlertService;
import greenhouse.c.DosageRecord;
import greenhouse.c.DosageRepository;
import greenhouse.c.NutrientDispenser;

import java.util.List;

public class GreenhouseAdminFacade {

    public record SystemState(List<CycleRecord> cycles, List<IrrigationSlot> slots,
                              List<IrrigationRun> runs, List<DosageRecord> dosages,
                              List<AlertService.Alert> alerts, VentilationPolicy.Mode ventilationMode) {}

    private final GreenhouseFacade facade;
    private final CycleRepository cycles;
    private final SlotRepository slots;
    private final RunRepository runs;
    private final DosageRepository dosages;
    private final IrrigationController irrigation;
    private final NutrientDispenser dispenser;

    public GreenhouseAdminFacade(GreenhouseFacade facade, CycleRepository cycles, SlotRepository slots,
                                 RunRepository runs, DosageRepository dosages,
                                 IrrigationController irrigation, NutrientDispenser dispenser) {
        this.facade = facade;
        this.cycles = cycles;
        this.slots = slots;
        this.runs = runs;
        this.dosages = dosages;
        this.irrigation = irrigation;
        this.dispenser = dispenser;
    }

    public List<CycleRecord> listCycles() {
        return cycles.findAll();
    }

    public SystemState inspect() {
        return new SystemState(
                cycles.findAll(),
                slots.findAll(),
                runs.findAll(),
                dosages.findAll(),
                dosages.findAllAlerts(),
                runs.loadVentilation().current());
    }

    public CycleRecord compensate(long cycleId) {
        return facade.compensate(cycleId);
    }

    public void simulatePumpFailure(boolean faulty) {
        irrigation.simulatePumpFailure(faulty);
    }

    public void simulateDispenserFailure(boolean faulty) {
        dispenser.simulateDispenserFailure(faulty);
    }

    public void resetAll() {
        slots.clear();
        runs.clear();
        dosages.clear();
        cycles.clear();
        irrigation.simulatePumpFailure(false);
        dispenser.simulateDispenserFailure(false);
    }
}
