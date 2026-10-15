package greenhouse.a;

import java.util.Optional;
import java.util.regex.Pattern;

public class SoilSensorArray {

    private static final Pattern BED_ID = Pattern.compile("[A-Za-z]-\\d+");

    private final SlotRepository repository;

    public SoilSensorArray(SlotRepository repository) {
        this.repository = repository;
    }

    static void requireValidBedId(String bedId) {
        if (bedId == null || !BED_ID.matcher(bedId).matches()) {
            throw new IllegalArgumentException("Invalid bed id '" + bedId + "', expected format like B-3");
        }
    }

    public void recordMoisture(String bedId, double moisturePercent) {
        requireValidBedId(bedId);
        if (Double.isNaN(moisturePercent) || moisturePercent < 0 || moisturePercent > 100) {
            throw new IllegalArgumentException("Moisture must be within 0..100, got " + moisturePercent);
        }
        repository.saveReading(bedId, moisturePercent);
    }

    public Optional<Double> latestMoisture(String bedId) {
        requireValidBedId(bedId);
        return repository.latestMoisture(bedId);
    }
}
