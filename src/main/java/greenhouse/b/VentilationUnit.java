package greenhouse.b;

import greenhouse.b.RunRepository.VentilationState;
import greenhouse.b.VentilationPolicy.Mode;

/**
 * Сервис B: включает вентиляцию в нужном режиме и возвращает её в прежнее состояние (откат).
 * Прежний режим хранится в БД, поэтому откат работает и после рестарта.
 */
public class VentilationUnit {

    private final RunRepository repository;
    private final VentilationPolicy policy;

    public VentilationUnit(RunRepository repository, VentilationPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    /**
     * Включает режим, который требует политика. Если вентиляция уже была переключена и ещё
     * не возвращена, исходный режим сохраняется — повторное включение его не затирает.
     */
    public Mode engage(double temperatureC, double humidityPercent) {
        Mode target = policy.modeFor(temperatureC, humidityPercent);
        VentilationState state = repository.loadVentilation();
        Mode original = state.previous() != null ? state.previous() : state.current();
        repository.saveVentilation(new VentilationState(target, original));
        return target;
    }

    /** Возвращает прежний режим. Если откатывать нечего — ничего не делает. */
    public Mode restore() {
        VentilationState state = repository.loadVentilation();
        if (state.previous() == null) {
            return state.current();
        }
        repository.saveVentilation(new VentilationState(state.previous(), null));
        return state.previous();
    }

    /**
     * Фиксирует текущий режим как итоговый: цикл завершён успешно, откатывать больше нечего.
     * Без этого «прежний» режим предыдущего цикла мог бы по ошибке вернуться при откате следующего.
     */
    public Mode commit() {
        VentilationState state = repository.loadVentilation();
        if (state.previous() != null) {
            repository.saveVentilation(new VentilationState(state.current(), null));
        }
        return state.current();
    }

    public Mode currentMode() {
        return repository.loadVentilation().current();
    }
}
