import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import greenhouse.a.*;

class IrrigationPolicyTest {

    private final IrrigationPolicy policy = new IrrigationPolicy();

    @Test
    void dryBedNeedsWater() {
        assertTrue(policy.needsIrrigation(20));
    }

    @Test
    void wetBedDoesNotNeedWater() {
        assertFalse(policy.needsIrrigation(50));
    }

    @Test
    void thresholdItselfDoesNotTriggerIrrigation() {
        assertFalse(policy.needsIrrigation(IrrigationPolicy.DEFAULT_THRESHOLD));
    }

    @Test
    void litersAreProportionalToMoistureDeficit() {
        assertEquals(80.0, policy.litersFor(20), 1e-9);
    }

    @Test
    void litersAreCapped() {
        IrrigationPolicy capped = new IrrigationPolicy(35, 60, 2.0, 100);
        assertEquals(100.0, capped.litersFor(0), 1e-9);
    }

    @Test
    void noLitersWhenIrrigationNotNeeded() {
        assertEquals(0.0, policy.litersFor(70), 1e-9);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new IrrigationPolicy(60, 35, 2.0, 100));
        assertThrows(IllegalArgumentException.class, () -> new IrrigationPolicy(35, 60, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new IrrigationPolicy(35, 60, 2.0, -1));
    }
}
