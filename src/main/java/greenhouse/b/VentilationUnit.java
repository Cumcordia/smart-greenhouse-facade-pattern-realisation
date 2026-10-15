package greenhouse.b;

import greenhouse.b.RunRepository.VentilationState;
import greenhouse.b.VentilationPolicy.Mode;

public class VentilationUnit {

    private final RunRepository repository;
    private final VentilationPolicy policy;

    public VentilationUnit(RunRepository repository, VentilationPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public Mode engage(double temperatureC, double humidityPercent) {
        Mode target = policy.modeFor(temperatureC, humidityPercent);
        VentilationState state = repository.loadVentilation();
        Mode original = state.previous() != null ? state.previous() : state.current();
        repository.saveVentilation(new VentilationState(target, original));
        return target;
    }

    public Mode restore() {
        VentilationState state = repository.loadVentilation();
        if (state.previous() == null) {
            return state.current();
        }
        repository.saveVentilation(new VentilationState(state.previous(), null));
        return state.previous();
    }

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
