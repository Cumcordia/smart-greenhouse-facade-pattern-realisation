package greenhouse.a;

public class IrrigationPolicy {

    public static final double DEFAULT_THRESHOLD = 35.0;
    public static final double DEFAULT_TARGET = 60.0;
    public static final double DEFAULT_LITERS_PER_POINT = 2.0;
    public static final double DEFAULT_MAX_LITERS = 150.0;

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

    public boolean needsIrrigation(double moisturePercent) {
        return moisturePercent < threshold;
    }

    public double litersFor(double moisturePercent) {
        if (!needsIrrigation(moisturePercent)) {
            return 0.0;
        }
        double liters = (target - moisturePercent) * litersPerPoint;
        return Math.min(liters, maxLiters);
    }
}
