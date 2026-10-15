package greenhouse.c;

import java.util.List;

/**
 * Сервис C: создаёт, отправляет и отменяет оповещения.
 *
 *   CREATED --> SENT --> CANCELLED
 *   CREATED -----------> CANCELLED
 *
 * Ошибки: IllegalArgumentException — плохой ввод (пустой текст, неизвестное оповещение);
 *         IllegalStateException — недопустимый переход.
 */
public class AlertService {

    public enum Status { CREATED, SENT, CANCELLED }

    public record Alert(long id, String message, Status status) {}

    private final DosageRepository repository;

    public AlertService(DosageRepository repository) {
        this.repository = repository;
    }

    public Alert create(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Alert message must not be blank");
        }
        long id = repository.insertAlert(message.trim(), Status.CREATED);
        return new Alert(id, message.trim(), Status.CREATED);
    }

    public Alert send(long alertId) {
        Alert alert = load(alertId);
        if (alert.status() != Status.CREATED) {
            throw new IllegalStateException("Cannot send alert in status " + alert.status());
        }
        return update(alert, Status.SENT);
    }

    /**
     * Отмена оповещения (откат C). Идемпотентна: повторный вызов для уже отменённого
     * оповещения ничего не меняет, чтобы фасад мог безопасно доводить откат после рестарта.
     */
    public Alert cancel(long alertId) {
        Alert alert = load(alertId);
        if (alert.status() == Status.CANCELLED) {
            return alert;
        }
        return update(alert, Status.CANCELLED);
    }

    public List<Alert> list() {
        return repository.findAllAlerts();
    }

    private Alert load(long alertId) {
        return repository.findAlert(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alert " + alertId));
    }

    private Alert update(Alert alert, Status status) {
        repository.updateAlertStatus(alert.id(), status);
        return new Alert(alert.id(), alert.message(), status);
    }
}
