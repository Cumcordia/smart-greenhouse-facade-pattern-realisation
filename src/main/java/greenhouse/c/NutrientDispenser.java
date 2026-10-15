package greenhouse.c;

import java.util.regex.Pattern;

/**
 * Сервис C: дозирует удобрение и откатывает дозу.
 * Подсистема C ничего не знает про A и B: на вход приходят обычные значения
 * (грядка, объём воды в литрах, название культуры).
 *
 * Ошибки:
 *  - IllegalArgumentException — плохой ввод (грядка, объём воды, культура, неизвестная доза);
 *  - IllegalStateException    — отказ дозатора или недопустимый переход статуса.
 */
public class NutrientDispenser {

    private static final Pattern BED_ID = Pattern.compile("[A-Za-z]-\\d+");

    private final DosageRepository repository;
    private final DosageCalculator calculator;
    private boolean dispenserFaulty;

    public NutrientDispenser(DosageRepository repository, DosageCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    /** Имитация отказа дозатора — нужна для сценария «принудительный сбой C» и для тестов. */
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

    /**
     * Откат C: возвращает дозу (статус REVERTED). Идемпотентен для уже возвращённой дозы —
     * фасад может безопасно доводить откат после рестарта.
     */
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
