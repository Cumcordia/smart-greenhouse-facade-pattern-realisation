package greenhouse.facade.exceptions;

public class NutrientFailedException extends GreenhouseException {

    private final long cycleId;

    public NutrientFailedException(long cycleId, String message, Throwable cause) {
        super(message, cause);
        this.cycleId = cycleId;
    }

    public long getCycleId() {
        return cycleId;
    }
}
