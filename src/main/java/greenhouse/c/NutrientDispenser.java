package greenhouse.c;

import java.util.regex.Pattern;

public class NutrientDispenser {

    private static final Pattern BED_ID = Pattern.compile("[A-Za-z]-\\d+");

    private final DosageRepository repository;
    private final DosageCalculator calculator;
    private boolean dispenserFaulty;

    public NutrientDispenser(DosageRepository repository, DosageCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public void simulateDispenserFailure(boolean faulty) {
        this.dispenserFaulty = faulty;
    }

    public DosageRecord dispense(String bedId, double waterLiters, String cropName) {
        if (bedId == null || !BED_ID.matcher(bedId).matches()) {
            throw new IllegalArgumentException("Invalid bed id '" + bedId + "', expected format like B-3");
        }
        DosageCalculator.Crop crop = DosageCalculator.Crop.fromName(cropName);
        double grams = calculator.gramsFor(crop, waterLiters);
        if (dispenserFaulty) {
            throw new IllegalStateException("Nutrient dispenser failure");
        }

        DosageRecord record = DosageRecord.plan(bedId, crop, waterLiters, grams);
        repository.save(record);      // PLANNED попадает на диск до «физической» выдачи
        record.dispense();
        repository.save(record);      // DISPENSED — после
        return record;
    }

    public DosageRecord revert(long dosageId) {
        DosageRecord record = repository.findById(dosageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dosage " + dosageId));
        if (record.getStatus() == DosageRecord.Status.REVERTED) {
            return record;
        }
        record.revert();
        repository.save(record);
        return record;
    }
}
