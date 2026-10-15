package greenhouse.facade.exceptions;

/** Базовое исключение фасадного слоя теплицы. Все ошибки цепочки A → B → C наследуются от него. */
public class GreenhouseException extends RuntimeException {

    public GreenhouseException(String message) {
        super(message);
    }

    public GreenhouseException(String message, Throwable cause) {
        super(message, cause);
    }
}
