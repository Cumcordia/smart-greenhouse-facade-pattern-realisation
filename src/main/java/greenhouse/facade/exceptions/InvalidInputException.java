package greenhouse.facade.exceptions;

/**
 * Плохой ввод или шаг A недоступен (доп. сценарий 3.6): неверная грядка, культура или показания,
 * грядка уже влажная, уже есть незавершённый цикл. Ничего не зафиксировано, откатывать нечего.
 */
public class InvalidInputException extends GreenhouseException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
