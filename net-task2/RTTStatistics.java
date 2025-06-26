import java.util.List;

public class RTTStatistics {
    public static double calculateStdDev(List<Long> values) {
        if (values.size() < 2) return 0;

        double mean = calculateMean(values);
        double variance = 0;

        for (long value : values) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= values.size();

        return Math.sqrt(variance);
    }

    public static double calculateMean(List<Long> values) {
        if (values.isEmpty()) return 0;

        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return (double) sum / values.size();
    }
}