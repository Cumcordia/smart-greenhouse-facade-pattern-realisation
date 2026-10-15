package greenhouse.b;

import java.util.regex.Pattern;

/**
 * Сервис B: запускает и останавливает полив, а также откатывает запуск (откат B).
 * Подсистема B ничего не знает про A: на вход приходят обычные значения (грядка, литры).
 *
 * Ошибки:
 *  - IllegalArgumentException — плохой ввод (формат грядки, литры, неизвестный запуск);
 *  - IllegalStateException    — правило не позволяет действие (грядка уже поливается,
 *                               отказ насоса, недопустимый переход статуса).
 */
public class IrrigationController {

    private static final Pattern BED_ID = Pattern.compile("[A-Za-z]-\\d+");

    private final RunRepository repository;
    private boolean pumpFaulty;

    public IrrigationController(RunRepository repository) {
        this.repository = repository;
    }

    /** Имитация отказа насоса — нужна для сценария «принудительный сбой B» и для тестов. */
    public void simulatePumpFailure(boolean faulty) {
        this.pumpFaulty = faulty;
    }

    public IrrigationRun start(String bedId, double liters) {
        if (bedId == null || !BED_ID.matcher(bedId).matches()) {
            throw new IllegalArgumentException("Invalid bed id '" + bedId + "', expected format like B-3");
        }
        if (Double.isNaN(liters) || liters <= 0) {
            throw new IllegalArgumentException("Liters must be positive, got " + liters);
        }
        if (pumpFaulty) {
            throw new IllegalStateException("Irrigation pump failure");
        }
        if (repository.findActiveByBed(bedId).isPresent()) {
            throw new IllegalStateException("Bed " + bedId + " is already being irrigated");
        }

        IrrigationRun run = IrrigationRun.start(bedId, liters);
        repository.save(run);
        return run;
    }

    /** Полив успешно завершён (вызывается, когда вся цепочка прошла). */
    public IrrigationRun complete(long runId) {
        IrrigationRun run = load(runId);
        run.complete();
        repository.save(run);
        return run;
    }

    /**
     * Откат B: останавливает полив. Идемпотентен для уже остановленного запуска —
     * фасад может безопасно доводить откат после рестарта. Завершённый (COMPLETED)
     * запуск остановить нельзя: это IllegalStateException.
     */
    public IrrigationRun abort(long runId) {
        IrrigationRun run = load(runId);
        if (run.getStatus() == IrrigationRun.Status.ABORTED) {
            return run;
        }
        run.abort();
        repository.save(run);
        return run;
    }

    private IrrigationRun load(long runId) {
        return repository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
    }
}
