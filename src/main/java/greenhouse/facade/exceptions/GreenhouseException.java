package greenhouse.facade.exceptions;

public class GreenhouseException extends RuntimeException {

    public GreenhouseException(String message) {
        super(message);
    }

    public GreenhouseException(String message, Throwable cause) {
        super(message, cause);
    }
}
