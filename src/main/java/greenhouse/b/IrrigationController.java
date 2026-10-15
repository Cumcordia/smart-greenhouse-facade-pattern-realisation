package greenhouse.b;

import java.util.regex.Pattern;

public class IrrigationController {

    private static final Pattern BED_ID = Pattern.compile("[A-Za-z]-\\d+");

    private final RunRepository repository;
    private boolean pumpFaulty;

    public IrrigationController(RunRepository repository) {
        this.repository = repository;
    }

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

    public IrrigationRun complete(long runId) {
        IrrigationRun run = load(runId);
        run.complete();
        repository.save(run);
        return run;
    }

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
