import static org.junit.jupiter.api.Assertions.*;

import greenhouse.c.DosageCalculator.Crop;
import org.junit.jupiter.api.Test;
import greenhouse.c.*;

class DosageRecordTest {

    private DosageRecord newRecord() {
        return DosageRecord.plan("B-3", Crop.TOMATO, 80, 96);
    }

    @Test
    void newRecordIsPlanned() {
        DosageRecord record = newRecord();
        assertEquals(DosageRecord.Status.PLANNED, record.getStatus());
        assertNull(record.getId());
    }

    @Test
    void plannedCanBeDispensed() {
        DosageRecord record = newRecord();
        record.dispense();
        assertEquals(DosageRecord.Status.DISPENSED, record.getStatus());
    }

    @Test
    void plannedCanBeReverted() {
        DosageRecord record = newRecord();
        record.revert();
        assertEquals(DosageRecord.Status.REVERTED, record.getStatus());
    }

    @Test
    void dispensedCanBeReverted() {
        DosageRecord record = newRecord();
        record.dispense();
        record.revert();
        assertEquals(DosageRecord.Status.REVERTED, record.getStatus());
    }

    @Test
    void dispenseTwiceIsIllegal() {
        DosageRecord record = newRecord();
        record.dispense();
        assertThrows(IllegalStateException.class, record::dispense);
    }

    @Test
    void revertedRecordCannotChangeAnymore() {
        DosageRecord record = newRecord();
        record.revert();
        assertThrows(IllegalStateException.class, record::dispense);
        assertThrows(IllegalStateException.class, record::revert);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> DosageRecord.plan(" ", Crop.TOMATO, 80, 96));
        assertThrows(IllegalArgumentException.class, () -> DosageRecord.plan("B-3", null, 80, 96));
        assertThrows(IllegalArgumentException.class, () -> DosageRecord.plan("B-3", Crop.TOMATO, 0, 96));
        assertThrows(IllegalArgumentException.class, () -> DosageRecord.plan("B-3", Crop.TOMATO, 80, 0));
    }

    @Test
    void toStringMentionsBedAndStatus() {
        String text = newRecord().toString();
        assertTrue(text.contains("B-3"));
        assertTrue(text.contains("PLANNED"));
    }
}
