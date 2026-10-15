package greenhouse.c;

/**
 * Чистая бизнес-логика подсистемы C: сколько граммов удобрения нужно
 * на заданный объём воды для данной культуры. Ничего не хранит.
 */
public class DosageCalculator {

    /** Культура и норма удобрения в граммах на 100 литров воды. */
    public enum Crop {
        TOMATO(120.0),
        CUCUMBER(100.0),
        PEPPER(110.0),
        LETTUCE(60.0);

        private final double gramsPer100Liters;

        Crop(double gramsPer100Liters) {
            this.gramsPer100Liters = gramsPer100Liters;
        }

        public double gramsPer100Liters() {
            return gramsPer100Liters;
        }

        /** Разбирает название без учёта регистра и пробелов; бросает IllegalArgumentException. */
        public static Crop fromName(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Crop must not be blank");
            }
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown crop '" + name + "', expected one of: "
                        + java.util.Arrays.toString(values()));
            }
        }
    }

    /** Потолок разовой дозы, г. */
    public static final double MAX_GRAMS = 200.0;

    /** Доза в граммах, округлённая до 0.1 г и ограниченная MAX_GRAMS. */
    public double gramsFor(Crop crop, double waterLiters) {
        if (crop == null) {
            throw new IllegalArgumentException("Crop must not be null");
        }
        if (Double.isNaN(waterLiters) || waterLiters <= 0) {
            throw new IllegalArgumentException("Water volume must be positive, got " + waterLiters);
        }
        double grams = waterLiters / 100.0 * crop.gramsPer100Liters();
        grams = Math.round(grams * 10.0) / 10.0;
        return Math.min(grams, MAX_GRAMS);
    }
}
