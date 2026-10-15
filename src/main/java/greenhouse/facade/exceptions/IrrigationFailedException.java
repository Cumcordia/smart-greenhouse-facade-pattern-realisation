package greenhouse.facade.exceptions;

public class IrrigationFailedException extends GreenhouseException {

    private final long cycleId;

    public IrrigationFailedException(long cycleId, String message, Throwable cause) {
        super(message, cause);
        this.cycleId = cycleId;
    }

    public long getCycleId() {
        return cycleId;
    }
}
