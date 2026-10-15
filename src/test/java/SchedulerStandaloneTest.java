import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import greenhouse.a.*;

/**
 * Подсистема A сама по себе, без фасада и без B/C.
 * Каждый тест получает свежий файл БД (@TempDir) — независимость и повторяемость по F.I.R.S.T.
 */
class SchedulerStandaloneTest {

    @TempDir
    Path tmp;

    private String dbPath;
    private SlotRepository repository;
    private SoilSensorArray sensors;
    private IrrigationScheduler scheduler;

    @BeforeEach
    void setUp() {
        dbPath = tmp.resolve("a.db").toString();
        repository = new SlotRepository(dbPath);
        sensors = new SoilSensorArray(repository);
        scheduler = new IrrigationScheduler(repository, sensors, new IrrigationPolicy());
    }

    @Test
    void reservesSlotForDryBed() {
        sensors.recordMoisture("B-3", 20);

        IrrigationSlot slot = scheduler.reserve("B-3");

        assertNotNull(slot.getId());
        assertEquals(IrrigationSlot.Status.RESERVED, slot.getStatus());
        assertEquals(80.0, slot.getLiters(), 1e-9);
    }

    @Test
    void confirmThenReleaseUpdatesPersistedStatus() {
        sensors.recordMoisture("B-3", 20);
        long id = scheduler.reserve("B-3").getId();

        scheduler.confirm(id);
        assertEquals(IrrigationSlot.Status.CONFIRMED, repository.findById(id).orElseThrow().getStatus());

        scheduler.release(id);
        assertEquals(IrrigationSlot.Status.RELEASED, repository.findById(id).orElseThrow().getStatus());
    }

    @Test
    void releaseIsIdempotent() {
        sensors.recordMoisture("B-3", 20);
        long id = scheduler.reserve("B-3").getId();

        scheduler.release(id);
        IrrigationSlot again = scheduler.release(id);

        assertEquals(IrrigationSlot.Status.RELEASED, again.getStatus());
    }

    @Test
    void refusesWhenNoReadingExists() {
        assertThrows(IllegalStateException.class, () -> scheduler.reserve("B-3"));
    }

    @Test
    void refusesWhenBedIsWetEnough() {
        sensors.recordMoisture("B-3", 55);
        assertThrows(IllegalStateException.class, () -> scheduler.reserve("B-3"));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void refusesSecondActiveSlotForSameBed() {
        sensors.recordMoisture("B-3", 20);
        scheduler.reserve("B-3");
        assertThrows(IllegalStateException.class, () -> scheduler.reserve("B-3"));
    }

    @Test
    void bedBecomesAvailableAgainAfterRelease() {
        sensors.recordMoisture("B-3", 20);
        long id = scheduler.reserve("B-3").getId();
        scheduler.release(id);

        assertNotEquals(id, scheduler.reserve("B-3").getId());
    }

    @Test
    void usesTheLatestReading() {
        sensors.recordMoisture("B-3", 20);
        sensors.recordMoisture("B-3", 50); // после полива/дождя стало влажно
        assertThrows(IllegalStateException.class, () -> scheduler.reserve("B-3"));
    }

    @Test
    void rejectsInvalidBedIdAndMoisture() {
        assertThrows(IllegalArgumentException.class, () -> sensors.recordMoisture("bed3", 20));
        assertThrows(IllegalArgumentException.class, () -> sensors.recordMoisture("B-3", 120));
        assertThrows(IllegalArgumentException.class, () -> sensors.recordMoisture("B-3", -1));
        assertThrows(IllegalArgumentException.class, () -> scheduler.reserve(null));
    }

    @Test
    void unknownSlotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> scheduler.confirm(999));
        assertThrows(IllegalArgumentException.class, () -> scheduler.release(999));
    }

    @Test
    void confirmedSlotDoesNotBlockNextCycle() {
        sensors.recordMoisture("B-3", 20);
        long first = scheduler.reserve("B-3").getId();
        scheduler.confirm(first);

        assertNotEquals(first, scheduler.reserve("B-3").getId());
    }

    @Test
    void stateSurvivesRestart() {
        sensors.recordMoisture("B-3", 20);
        long id = scheduler.reserve("B-3").getId();

        // «рестарт»: новые объекты поверх того же файла
        SlotRepository reopened = new SlotRepository(dbPath);
        IrrigationScheduler restarted =
                new IrrigationScheduler(reopened, new SoilSensorArray(reopened), new IrrigationPolicy());

        IrrigationSlot loaded = reopened.findById(id).orElseThrow();
        assertEquals(IrrigationSlot.Status.RESERVED, loaded.getStatus());
        assertEquals("B-3", loaded.getBedId());
        // бизнес-правило видит занятую грядку после рестарта
        assertThrows(IllegalStateException.class, () -> restarted.reserve("B-3"));
        // и откат после рестарта работает
        restarted.release(id);
        assertEquals(IrrigationSlot.Status.RELEASED, reopened.findById(id).orElseThrow().getStatus());
    }

    @Test
    void clearWipesSlotsAndReadings() {
        sensors.recordMoisture("B-3", 20);
        scheduler.reserve("B-3");

        repository.clear();

        assertTrue(repository.findAll().isEmpty());
        assertTrue(sensors.latestMoisture("B-3").isEmpty());
    }
}
