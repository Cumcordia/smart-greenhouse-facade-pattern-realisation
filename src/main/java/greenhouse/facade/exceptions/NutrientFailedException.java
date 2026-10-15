package greenhouse.facade.exceptions;

/** Сбой шага C (удобрение / оповещение). К моменту выброса исключения шаги B и A уже откачены. */
public class NutrientFailedException extends GreenhouseException {

    private final long cycleId;

    public NutrientFailedException(long cycleId, String message, Throwable cause) {
        super(message, cause);
        this.cycleId = cycleId;
    }

    /** Id цикла (запись в таблице cycles), который был откачен. */
    public long getCycleId() {
        return cycleId;
    }
}
