import static org.junit.jupiter.api.Assertions.*;

import greenhouse.c.DosageCalculator.Crop;
import org.junit.jupiter.api.Test;
import greenhouse.c.*;

class DosageCalculatorTest {

    private final DosageCalculator calculator = new DosageCalculator();

    @Test
    void doseIsProportionalToWaterVolume() {
        assertEquals(96.0, calculator.gramsFor(Crop.TOMATO, 80), 1e-9);
    }

    @Test
    void doseDependsOnCrop() {
        assertEquals(48.0, calculator.gramsFor(Crop.LETTUCE, 80), 1e-9);
        assertEquals(80.0, calculator.gramsFor(Crop.CUCUMBER, 80), 1e-9);
        assertEquals(88.0, calculator.gramsFor(Crop.PEPPER, 80), 1e-9);
    }

    @Test
    void doseIsRoundedToOneDecimal() {
        assertEquals(39.6, calculator.gramsFor(Crop.TOMATO, 33), 1e-9);
        assertEquals(4.7, calculator.gramsFor(Crop.LETTUCE, 7.77), 1e-9);
    }

    @Test
    void doseIsCapped() {
        assertEquals(DosageCalculator.MAX_GRAMS, calculator.gramsFor(Crop.TOMATO, 250), 1e-9);
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> calculator.gramsFor(null, 80));
        assertThrows(IllegalArgumentException.class, () -> calculator.gramsFor(Crop.TOMATO, 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.gramsFor(Crop.TOMATO, -5));
        assertThrows(IllegalArgumentException.class, () -> calculator.gramsFor(Crop.TOMATO, Double.NaN));
    }

    @Test
    void cropNameParsingIgnoresCaseAndSpaces() {
        assertEquals(Crop.TOMATO, Crop.fromName("  tomato "));
        assertEquals(Crop.LETTUCE, Crop.fromName("Lettuce"));
    }

    @Test
    void unknownOrBlankCropIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Crop.fromName("banana"));
        assertThrows(IllegalArgumentException.class, () -> Crop.fromName(" "));
        assertThrows(IllegalArgumentException.class, () -> Crop.fromName(null));
    }
}
