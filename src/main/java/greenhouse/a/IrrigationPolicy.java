package greenhouse.a;

/**
 * Чистая бизнес-логика подсистемы A: нужен ли полив и сколько литров.
 * Не знает ни про БД, ни про слоты — только числа на входе и на выходе.
 */
public class IrrigationPolicy {

    public static final double DEFAULT_THRESHOLD = 35.0;      // ниже — почва сухая, %
    public static final double DEFAULT_TARGET = 60.0;         // до какой влажности поливаем, %
    public static final double DEFAULT_LITERS_PER_POINT = 2.0; // литров на 1 п.п. влажности
    public static final double DEFAULT_MAX_LITERS = 150.0;    // потолок за один полив

    private final double threshold;
    private final double target;
    private final double litersPerPoint;
    private final double maxLiters;

    public IrrigationPolicy() {
        this(DEFAULT_THRESHOLD, DEFAULT_TARGET, DEFAULT_LITERS_PER_POINT, DEFAULT_MAX_LITERS);
    }

    public IrrigationPolicy(double threshold, double target, double litersPerPoint, double maxLiters) {
        if (threshold >= target) {
            throw new IllegalArgumentException("threshold must be below target");
        }
        if (litersPerPoint <= 0 || maxLiters <= 0) {
            throw new IllegalArgumentException("litersPerPoint and maxLiters must be positive");
        }
        this.threshold = threshold;
        this.target = target;
        this.litersPerPoint = litersPerPoint;
        this.maxLiters = maxLiters;
    }

    /** Полив нужен, если влажность строго ниже порога. */
    public boolean needsIrrigation(double moisturePercent) {
        return moisturePercent < threshold;
    }

    /** Сколько литров нужно, чтобы довести грядку до целевой влажности; 0, если полив не нужен. */
    public double litersFor(double moisturePercent) {
        if (!needsIrrigation(moisturePercent)) {
            return 0.0;
        }
        double liters = (target - moisturePercent) * litersPerPoint;
        return Math.min(liters, maxLiters);
    }
}
