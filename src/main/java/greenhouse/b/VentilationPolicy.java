package greenhouse.b;

public class VentilationPolicy {

    public enum Mode { OFF, LOW, HIGH }

    public static final double HIGH_TEMPERATURE = 30.0;
    public static final double HIGH_HUMIDITY = 80.0;
    public static final double LOW_TEMPERATURE = 25.0;
    public static final double LOW_HUMIDITY = 65.0;

    public Mode modeFor(double temperatureC, double humidityPercent) {
        if (Double.isNaN(temperatureC) || temperatureC < -50 || temperatureC > 80) {
            throw new IllegalArgumentException("Temperature must be within -50..80 C, got " + temperatureC);
        }
        if (Double.isNaN(humidityPercent) || humidityPercent < 0 || humidityPercent > 100) {
            throw new IllegalArgumentException("Humidity must be within 0..100, got " + humidityPercent);
        }
        if (temperatureC >= HIGH_TEMPERATURE || humidityPercent >= HIGH_HUMIDITY) {
            return Mode.HIGH;
        }
        if (temperatureC >= LOW_TEMPERATURE || humidityPercent >= LOW_HUMIDITY) {
            return Mode.LOW;
        }
        return Mode.OFF;
    }
}
