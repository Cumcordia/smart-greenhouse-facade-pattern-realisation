import static org.junit.jupiter.api.Assertions.*;

import greenhouse.c.AlertService.Alert;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import greenhouse.c.*;

class DispenserStandaloneTest {

    @TempDir
    Path tmp;

    private String dbPath;
    private DosageRepository repository;
    private NutrientDispenser dispenser;
    private AlertService alerts;

    @BeforeEach
    void setUp() {
        dbPath = tmp.resolve("c.db").toString();
        repository = new DosageRepository(dbPath);
        dispenser = new NutrientDispenser(repository, new DosageCalculator());
        alerts = new AlertService(repository);
    }

    @Test
    void dispenseCreatesPersistedDosage() {
        DosageRecord record = dispenser.dispense("B-3", 80, "tomato");

        assertNotNull(record.getId());
        assertEquals(DosageRecord.Status.DISPENSED, record.getStatus());
        assertEquals(96.0, record.getGrams(), 1e-9);
        assertEquals(DosageRecord.Status.DISPENSED,
                repository.findById(record.getId()).orElseThrow().getStatus());
    }

    @Test
    void revertUpdatesPersistedStatusAndIsIdempotent() {
        long id = dispenser.dispense("B-3", 80, "tomato").getId();

        dispenser.revert(id);
        DosageRecord again = dispenser.revert(id);

        assertEquals(DosageRecord.Status.REVERTED, again.getStatus());
        assertEquals(DosageRecord.Status.REVERTED, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void sameBedCanBeDosedAgainAfterRevert() {
        long first = dispenser.dispense("B-3", 80, "tomato").getId();
        dispenser.revert(first);
        assertNotEquals(first, dispenser.dispense("B-3", 80, "tomato").getId());
    }

    @Test
    void dispenserFailureLeavesNothingBehind() {
        dispenser.simulateDispenserFailure(true);

        assertThrows(IllegalStateException.class, () -> dispenser.dispense("B-3", 80, "tomato"));
        assertTrue(repository.findAll().isEmpty());

        dispenser.simulateDispenserFailure(false);
        assertDoesNotThrow(() -> dispenser.dispense("B-3", 80, "tomato"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> dispenser.dispense("bed3", 80, "tomato"));
        assertThrows(IllegalArgumentException.class, () -> dispenser.dispense(null, 80, "tomato"));
        assertThrows(IllegalArgumentException.class, () -> dispenser.dispense("B-3", 0, "tomato"));
        assertThrows(IllegalArgumentException.class, () -> dispenser.dispense("B-3", 80, "banana"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void unknownDosageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> dispenser.revert(999));
    }

    @Test
    void alertGoesFromCreatedToSentToCancelled() {
        Alert created = alerts.create("Fertilizer dispensed for B-3");
        assertEquals(AlertService.Status.CREATED, created.status());

        assertEquals(AlertService.Status.SENT, alerts.send(created.id()).status());
        assertEquals(AlertService.Status.CANCELLED, alerts.cancel(created.id()).status());
        assertEquals(AlertService.Status.CANCELLED, repository.findAlert(created.id()).orElseThrow().status());
    }

    @Test
    void createdAlertCanBeCancelledWithoutSending() {
        long id = alerts.create("Fertilizer dispensed for B-3").id();
        assertEquals(AlertService.Status.CANCELLED, alerts.cancel(id).status());
    }

    @Test
    void cancelIsIdempotent() {
        long id = alerts.create("msg").id();
        alerts.cancel(id);
        assertEquals(AlertService.Status.CANCELLED, alerts.cancel(id).status());
    }

    @Test
    void sendIsOnlyAllowedForCreatedAlerts() {
        long id = alerts.create("msg").id();
        alerts.send(id);
        assertThrows(IllegalStateException.class, () -> alerts.send(id));

        long cancelled = alerts.create("msg 2").id();
        alerts.cancel(cancelled);
        assertThrows(IllegalStateException.class, () -> alerts.send(cancelled));
    }

    @Test
    void alertRejectsBlankMessageAndUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> alerts.create(" "));
        assertThrows(IllegalArgumentException.class, () -> alerts.create(null));
        assertThrows(IllegalArgumentException.class, () -> alerts.send(999));
        assertThrows(IllegalArgumentException.class, () -> alerts.cancel(999));
    }

    @Test
    void listReturnsAlertsInCreationOrder() {
        alerts.create("first");
        alerts.create("second");
        assertEquals(2, alerts.list().size());
        assertEquals("first", alerts.list().get(0).message());
    }

    @Test
    void stateSurvivesRestart() {
        long dosageId = dispenser.dispense("B-3", 80, "tomato").getId();
        long alertId = alerts.create("Fertilizer dispensed for B-3").id();
        alerts.send(alertId);

        // «рестарт»: новые объекты поверх того же файла
        DosageRepository reopened = new DosageRepository(dbPath);
        NutrientDispenser restartedDispenser = new NutrientDispenser(reopened, new DosageCalculator());
        AlertService restartedAlerts = new AlertService(reopened);

        assertEquals(DosageRecord.Status.DISPENSED, reopened.findById(dosageId).orElseThrow().getStatus());
        assertEquals(AlertService.Status.SENT, reopened.findAlert(alertId).orElseThrow().status());
        // откат после рестарта работает
        assertEquals(DosageRecord.Status.REVERTED, restartedDispenser.revert(dosageId).getStatus());
        assertEquals(AlertService.Status.CANCELLED, restartedAlerts.cancel(alertId).status());
    }

    @Test
    void clearWipesDosagesAndAlerts() {
        dispenser.dispense("B-3", 80, "tomato");
        alerts.create("msg");

        repository.clear();

        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findAllAlerts().isEmpty());
    }
}
