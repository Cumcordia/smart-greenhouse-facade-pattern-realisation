import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import greenhouse.a.*;

class IrrigationSlotTest {

    private IrrigationSlot newSlot() {
        return IrrigationSlot.reserve("B-3", 80);
    }

    @Test
    void newSlotIsReservedAndActive() {
        IrrigationSlot slot = newSlot();
        assertEquals(IrrigationSlot.Status.RESERVED, slot.getStatus());
        assertTrue(slot.isActive());
        assertNull(slot.getId());
    }

    @Test
    void reservedCanBeConfirmed() {
        IrrigationSlot slot = newSlot();
        slot.confirm();
        assertEquals(IrrigationSlot.Status.CONFIRMED, slot.getStatus());
    }

    @Test
    void reservedCanBeReleased() {
        IrrigationSlot slot = newSlot();
        slot.release();
        assertEquals(IrrigationSlot.Status.RELEASED, slot.getStatus());
        assertFalse(slot.isActive());
    }

    @Test
    void confirmedCanBeReleased() {
        IrrigationSlot slot = newSlot();
        slot.confirm();
        slot.release();
        assertEquals(IrrigationSlot.Status.RELEASED, slot.getStatus());
    }

    @Test
    void confirmedSlotNoLongerHoldsTheBed() {
        IrrigationSlot slot = newSlot();
        slot.confirm();
        assertFalse(slot.isActive());
    }

    @Test
    void confirmTwiceIsIllegal() {
        IrrigationSlot slot = newSlot();
        slot.confirm();
        assertThrows(IllegalStateException.class, slot::confirm);
    }

    @Test
    void releasedSlotCannotBeConfirmedOrReleasedAgain() {
        IrrigationSlot slot = newSlot();
        slot.release();
        assertThrows(IllegalStateException.class, slot::confirm);
        assertThrows(IllegalStateException.class, slot::release);
    }

    @Test
    void rejectsBlankBedAndNonPositiveLiters() {
        assertThrows(IllegalArgumentException.class, () -> IrrigationSlot.reserve(" ", 10));
        assertThrows(IllegalArgumentException.class, () -> IrrigationSlot.reserve(null, 10));
        assertThrows(IllegalArgumentException.class, () -> IrrigationSlot.reserve("B-3", 0));
    }

    @Test
    void toStringMentionsBedAndStatus() {
        String text = newSlot().toString();
        assertTrue(text.contains("B-3"));
        assertTrue(text.contains("RESERVED"));
    }
}
