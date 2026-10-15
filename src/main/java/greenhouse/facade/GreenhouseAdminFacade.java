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

/**
 * Узкий фасад для администратора: посмотреть всё сохранённое, откатить цикл вручную,
 * сбросить хранилище и включить/выключить имитацию сбоев B и C для демо.
 * Откат делегируется основному фасаду — это тот же проверенный код, а не вторая копия.
 */
public class GreenhouseAdminFacade {

    /** Снимок всего, что сейчас лежит на диске. */
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

    /** Читает с диска состояние всех трёх подсистем и хода цепочек. */
    public SystemState inspect() {
        return new SystemState(
                cycles.findAll(),
                slots.findAll(),
                runs.findAll(),
                dosages.findAll(),
                dosages.findAllAlerts(),
                runs.loadVentilation().current());
    }

    /** Ручной откат цикла по id. См. {@link GreenhouseFacade#compensate(long)}. */
    public CycleRecord compensate(long cycleId) {
        return facade.compensate(cycleId);
    }

    /** Имитация отказа насоса: следующий шаг B завершится сбоем (откат A). */
    public void simulatePumpFailure(boolean faulty) {
        irrigation.simulatePumpFailure(faulty);
    }

    /** Имитация отказа дозатора: следующий шаг C завершится сбоем (откат B, затем A). */
    public void simulateDispenserFailure(boolean faulty) {
        dispenser.simulateDispenserFailure(faulty);
    }

    /** Полный сброс: очищает хранилища A, B, C и ход цепочек, снимает имитацию сбоев. */
    public void resetAll() {
        slots.clear();
        runs.clear();
        dosages.clear();
        cycles.clear();
        irrigation.simulatePumpFailure(false);
        dispenser.simulateDispenserFailure(false);
    }
}
