import static org.junit.jupiter.api.Assertions.*;

import greenhouse.b.VentilationPolicy.Mode;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import greenhouse.b.*;

class ControllerStandaloneTest {

    @TempDir
    Path tmp;

    private String dbPath;
    private RunRepository repository;
    private IrrigationController controller;
    private VentilationUnit ventilation;

    @BeforeEach
    void setUp() {
        dbPath = tmp.resolve("b.db").toString();
        repository = new RunRepository(dbPath);
        controller = new IrrigationController(repository);
        ventilation = new VentilationUnit(repository, new VentilationPolicy());
    }

    @Test
    void startCreatesPersistedRun() {
        IrrigationRun run = controller.start("B-3", 80);

        assertNotNull(run.getId());
        assertEquals(IrrigationRun.Status.STARTED, run.getStatus());
        assertEquals(IrrigationRun.Status.STARTED, repository.findById(run.getId()).orElseThrow().getStatus());
    }

    @Test
    void completeUpdatesPersistedStatus() {
        long id = controller.start("B-3", 80).getId();
        controller.complete(id);
        assertEquals(IrrigationRun.Status.COMPLETED, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void abortUpdatesPersistedStatusAndIsIdempotent() {
        long id = controller.start("B-3", 80).getId();

        controller.abort(id);
        IrrigationRun again = controller.abort(id);

        assertEquals(IrrigationRun.Status.ABORTED, again.getStatus());
        assertEquals(IrrigationRun.Status.ABORTED, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void completedRunCannotBeAborted() {
        long id = controller.start("B-3", 80).getId();
        controller.complete(id);
        assertThrows(IllegalStateException.class, () -> controller.abort(id));
    }

    @Test
    void refusesSecondActiveRunForSameBed() {
        controller.start("B-3", 80);
        assertThrows(IllegalStateException.class, () -> controller.start("B-3", 50));
        // другая грядка не мешает
        assertDoesNotThrow(() -> controller.start("B-4", 50));
    }

    @Test
    void bedBecomesAvailableAgainAfterAbortOrComplete() {
        long first = controller.start("B-3", 80).getId();
        controller.abort(first);
        long second = controller.start("B-3", 80).getId();
        controller.complete(second);

        assertNotEquals(first, controller.start("B-3", 80).getId());
    }

    @Test
    void pumpFailureRefusesStartAndLeavesNothingBehind() {
        controller.simulatePumpFailure(true);

        assertThrows(IllegalStateException.class, () -> controller.start("B-3", 80));
        assertTrue(repository.findAll().isEmpty());

        controller.simulatePumpFailure(false);
        assertDoesNotThrow(() -> controller.start("B-3", 80));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> controller.start("bed3", 80));
        assertThrows(IllegalArgumentException.class, () -> controller.start(null, 80));
        assertThrows(IllegalArgumentException.class, () -> controller.start("B-3", 0));
        assertThrows(IllegalArgumentException.class, () -> controller.start("B-3", Double.NaN));
    }

    @Test
    void unknownRunIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.complete(999));
        assertThrows(IllegalArgumentException.class, () -> controller.abort(999));
    }

    @Test
    void ventilationStartsOff() {
        assertEquals(Mode.OFF, ventilation.currentMode());
    }

    @Test
    void engageSwitchesModeAndRestoreReturnsPrevious() {
        assertEquals(Mode.HIGH, ventilation.engage(32, 50));
        assertEquals(Mode.HIGH, ventilation.currentMode());

        assertEquals(Mode.OFF, ventilation.restore());
        assertEquals(Mode.OFF, ventilation.currentMode());
    }

    @Test
    void secondEngageKeepsTheOriginalPreviousMode() {
        ventilation.engage(26, 50);  // OFF -> LOW
        ventilation.engage(32, 50);  // LOW -> HIGH, но «прежним» остаётся OFF

        assertEquals(Mode.OFF, ventilation.restore());
    }

    @Test
    void restoreWithoutEngageChangesNothing() {
        assertEquals(Mode.OFF, ventilation.restore());
        assertEquals(Mode.OFF, ventilation.currentMode());
    }

    @Test
    void commitMakesCurrentModeFinal() {
        ventilation.engage(32, 50);                       // OFF -> HIGH, previous = OFF
        assertEquals(Mode.HIGH, ventilation.commit());

        assertEquals(Mode.HIGH, ventilation.restore());   // откатывать больше нечего
        ventilation.engage(20, 50);                       // HIGH -> OFF, previous = HIGH
        assertEquals(Mode.HIGH, ventilation.restore());
    }

    @Test
    void commitWithoutEngageChangesNothing() {
        assertEquals(Mode.OFF, ventilation.commit());
        assertEquals(Mode.OFF, ventilation.currentMode());
    }

    @Test
    void restoreTwiceIsSafe() {
        ventilation.engage(32, 50);
        ventilation.restore();
        assertEquals(Mode.OFF, ventilation.restore());
    }

    @Test
    void engageRejectsImpossibleValuesWithoutChangingState() {
        assertThrows(IllegalArgumentException.class, () -> ventilation.engage(20, 150));
        assertEquals(Mode.OFF, ventilation.currentMode());
    }


    @Test
    void stateSurvivesRestart() {
        long id = controller.start("B-3", 80).getId();
        ventilation.engage(32, 50);

        RunRepository reopened = new RunRepository(dbPath);
        IrrigationController restartedController = new IrrigationController(reopened);
        VentilationUnit restartedVentilation = new VentilationUnit(reopened, new VentilationPolicy());

        assertEquals(IrrigationRun.Status.STARTED, reopened.findById(id).orElseThrow().getStatus());
        assertEquals(Mode.HIGH, restartedVentilation.currentMode());
        assertThrows(IllegalStateException.class, () -> restartedController.start("B-3", 50));
        restartedController.abort(id);
        assertEquals(Mode.OFF, restartedVentilation.restore());
    }

    @Test
    void clearWipesRunsAndVentilation() {
        controller.start("B-3", 80);
        ventilation.engage(32, 50);

        repository.clear();

        assertTrue(repository.findAll().isEmpty());
        assertEquals(Mode.OFF, ventilation.currentMode());
    }
}
