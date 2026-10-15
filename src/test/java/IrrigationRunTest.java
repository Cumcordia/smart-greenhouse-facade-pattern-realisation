import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import greenhouse.b.*;

class IrrigationRunTest {

    private IrrigationRun newRun() {
        return IrrigationRun.start("B-3", 80);
    }

    @Test
    void newRunIsStartedAndActive() {
        IrrigationRun run = newRun();
        assertEquals(IrrigationRun.Status.STARTED, run.getStatus());
        assertTrue(run.isActive());
        assertNull(run.getId());
    }

    @Test
    void startedCanBeCompleted() {
        IrrigationRun run = newRun();
        run.complete();
        assertEquals(IrrigationRun.Status.COMPLETED, run.getStatus());
        assertFalse(run.isActive());
    }

    @Test
    void startedCanBeAborted() {
        IrrigationRun run = newRun();
        run.abort();
        assertEquals(IrrigationRun.Status.ABORTED, run.getStatus());
        assertFalse(run.isActive());
    }

    @Test
    void completedRunCannotChangeAnymore() {
        IrrigationRun run = newRun();
        run.complete();
        assertThrows(IllegalStateException.class, run::complete);
        assertThrows(IllegalStateException.class, run::abort);
    }

    @Test
    void abortedRunCannotChangeAnymore() {
        IrrigationRun run = newRun();
        run.abort();
        assertThrows(IllegalStateException.class, run::complete);
        assertThrows(IllegalStateException.class, run::abort);
    }

    @Test
    void rejectsBlankBedAndNonPositiveLiters() {
        assertThrows(IllegalArgumentException.class, () -> IrrigationRun.start(" ", 10));
        assertThrows(IllegalArgumentException.class, () -> IrrigationRun.start(null, 10));
        assertThrows(IllegalArgumentException.class, () -> IrrigationRun.start("B-3", 0));
    }

    @Test
    void toStringMentionsBedAndStatus() {
        String text = newRun().toString();
        assertTrue(text.contains("B-3"));
        assertTrue(text.contains("STARTED"));
    }
}
