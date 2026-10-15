package greenhouse.facade;

import greenhouse.a.IrrigationScheduler;
import greenhouse.a.IrrigationSlot;
import greenhouse.a.SoilSensorArray;
import greenhouse.b.IrrigationController;
import greenhouse.b.IrrigationRun;
import greenhouse.b.VentilationPolicy;
import greenhouse.b.VentilationUnit;
import greenhouse.c.AlertService;
import greenhouse.c.DosageCalculator;
import greenhouse.c.DosageRecord;
import greenhouse.c.NutrientDispenser;
import greenhouse.facade.exceptions.GreenhouseException;
import greenhouse.facade.exceptions.InvalidInputException;
import greenhouse.facade.exceptions.IrrigationFailedException;
import greenhouse.facade.exceptions.NutrientFailedException;
import java.util.List;
import java.util.Locale;

/**
 * Основной фасад: один цикл ухода за грядкой как одна операция для клиента.
 *
 *   A: записать показание влажности, зарезервировать слот полива
 *   B: запустить полив, включить вентиляцию
 *   C: выдать удобрение, создать и отправить оповещение
 *   финал: завершить полив, зафиксировать вентиляцию, подтвердить слот
 *
 * Ход цепочки пишется на диск (CycleRecord) после каждого шага, поэтому откат определяется
 * по тому, что реально сохранено: сбой B → откат A; сбой C → откат B, затем A.
 */
public class GreenhouseFacade {

    /** Ввод клиента для одного цикла. */
    public record CycleRequest(String bedId, double moisturePercent, String crop,
                               double temperatureC, double humidityPercent) {}

    private final SoilSensorArray sensors;
    private final IrrigationScheduler scheduler;
    private final IrrigationController irrigation;
    private final VentilationUnit ventilation;
    private final NutrientDispenser dispenser;
    private final AlertService alerts;
    private final CycleRepository cycles;
    private final VentilationPolicy ventilationPolicy = new VentilationPolicy();

    public GreenhouseFacade(SoilSensorArray sensors, IrrigationScheduler scheduler,
                            IrrigationController irrigation, VentilationUnit ventilation,
                            NutrientDispenser dispenser, AlertService alerts, CycleRepository cycles) {
        this.sensors = sensors;
        this.scheduler = scheduler;
        this.irrigation = irrigation;
        this.ventilation = ventilation;
        this.dispenser = dispenser;
        this.alerts = alerts;
        this.cycles = cycles;
    }

    /**
     * Прогоняет всю цепочку A → B → C.
     *
     * @throws InvalidInputException      плохой ввод или шаг A недоступен — ничего не зафиксировано
     * @throws IrrigationFailedException  сбой B — шаг A уже откачен
     * @throws NutrientFailedException    сбой C — шаги B и A уже откачены
     */
    public CycleRecord runCycle(CycleRequest request) {
        String crop = validate(request);
        String bedId = request.bedId();

        // ---------- шаг A: показание + резерв слота ----------
        IrrigationSlot slot;
        try {
            sensors.recordMoisture(bedId, request.moisturePercent());
            slot = scheduler.reserve(bedId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new InvalidInputException(e.getMessage(), e);
        }
        CycleRecord cycle = CycleRecord.begin(bedId, crop, request.temperatureC(),
                request.humidityPercent(), slot.getId());
        cycles.save(cycle);                                   // PENDING_B

        // ---------- шаг B: полив + вентиляция ----------
        try {
            IrrigationRun run = irrigation.start(bedId, slot.getLiters());
            cycle.attachRun(run.getId());
            cycles.save(cycle);                               // запуск записан до вентиляции
            ventilation.engage(request.temperatureC(), request.humidityPercent());
            cycle.advanceToPendingC();
            cycles.save(cycle);                               // PENDING_C
        } catch (RuntimeException e) {
            compensate(cycle, "Step B failed: " + e.getMessage());
            throw new IrrigationFailedException(cycle.getId(),
                    "Step B (irrigation/ventilation) failed: " + e.getMessage()
                            + " — cycle " + cycle.getId() + " compensated (A rolled back)", e);
        }

        // ---------- шаг C: удобрение + оповещение ----------
        try {
            DosageRecord dosage = dispenser.dispense(bedId, slot.getLiters(), crop);
            cycle.attachDosage(dosage.getId());
            cycles.save(cycle);
            AlertService.Alert alert = alerts.create(String.format(Locale.ROOT,
                    "Fertilizer dispensed for %s: %.1f g (%s)", bedId, dosage.getGrams(), crop));
            cycle.attachAlert(alert.id());
            cycles.save(cycle);
            alerts.send(alert.id());
        } catch (RuntimeException e) {
            compensate(cycle, "Step C failed: " + e.getMessage());
            throw new NutrientFailedException(cycle.getId(),
                    "Step C (nutrients/alert) failed: " + e.getMessage()
                            + " — cycle " + cycle.getId() + " compensated (B and A rolled back)", e);
        }

        // ---------- финал: цепочка прошла, фиксируем результат ----------
        try {
            irrigation.complete(cycle.getRunId());
            ventilation.commit();
            scheduler.confirm(cycle.getSlotId());
            cycle.complete();
            cycles.save(cycle);                               // COMPLETED
        } catch (RuntimeException e) {
            throw new GreenhouseException("Cycle " + cycle.getId() + ": all steps succeeded but finalization "
                    + "failed; the cycle stays " + cycle.getStatus() + " (recover or compensate it)", e);
        }
        return cycle;
    }

    /**
     * Откат цепочки вручную (по id). Идемпотентен для уже откаченного цикла; завершённый
     * цикл откатить нельзя.
     */
    public CycleRecord compensate(long cycleId) {
        CycleRecord cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new InvalidInputException("Unknown cycle " + cycleId));
        if (cycle.getStatus() == CycleRecord.Status.COMPENSATED) {
            return cycle;
        }
        if (cycle.getStatus() == CycleRecord.Status.COMPLETED) {
            throw new InvalidInputException("Cycle " + cycleId + " is COMPLETED and cannot be compensated");
        }
        return compensate(cycle, "Manual compensation");
    }

    /**
     * Восстановление после рестарта: берёт с диска все незавершённые цепочки (PENDING_B / PENDING_C)
     * и откатывает каждую на глубину, записанную в её CycleRecord.
     *
     * @return откаченные циклы (пусто, если всё было завершено)
     */
    public List<CycleRecord> recoverInterrupted() {
        List<CycleRecord> inFlight = cycles.findInFlight();
        for (CycleRecord cycle : inFlight) {
            compensate(cycle, "Interrupted before completion (recovered after restart)");
        }
        return inFlight;
    }

    /**
     * Откат на глубину, которую подсказывает записанный ход цепочки, строго в обратном порядке:
     * C (доза, оповещение) → B (полив, вентиляция) → A (слот). Все операции отката идемпотентны.
     */
    private CycleRecord compensate(CycleRecord cycle, String reason) {
        ChainStep reached = cycle.farthestStep();
        try {
            if (reached.isAtLeast(ChainStep.C)) {
                if (cycle.getDosageId() != null) {
                    dispenser.revert(cycle.getDosageId());
                }
                if (cycle.getAlertId() != null) {
                    alerts.cancel(cycle.getAlertId());
                }
            }
            if (reached.isAtLeast(ChainStep.B)) {
                if (cycle.getRunId() != null) {
                    irrigation.abort(cycle.getRunId());
                }
                ventilation.restore();
            }
            scheduler.release(cycle.getSlotId());
        } catch (RuntimeException e) {
            throw new GreenhouseException("Compensation of cycle " + cycle.getId() + " (reached step "
                    + reached + ") failed; the cycle stays " + cycle.getStatus()
                    + " and can be retried: " + e.getMessage(), e);
        }
        cycle.compensate(reason);
        cycles.save(cycle);
        return cycle;
    }

    /** Проверяет то, что можно проверить до первого коммита; возвращает каноничное имя культуры. */
    private String validate(CycleRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request must not be null");
        }
        try {
            String crop = DosageCalculator.Crop.fromName(request.crop()).name();
            ventilationPolicy.modeFor(request.temperatureC(), request.humidityPercent());
            return crop;
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(e.getMessage(), e);
        }
    }
}
