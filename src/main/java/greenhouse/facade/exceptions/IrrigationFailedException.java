package greenhouse.facade.exceptions;

/** Сбой шага B (полив / вентиляция). К моменту выброса исключения шаг A уже откачен. */
public class IrrigationFailedException extends GreenhouseException {

    private final long cycleId;

    public IrrigationFailedException(long cycleId, String message, Throwable cause) {
        super(message, cause);
        this.cycleId = cycleId;
    }

    /** Id цикла (запись в таблице cycles), который был откачен. */
    public long getCycleId() {
        return cycleId;
    }
}
