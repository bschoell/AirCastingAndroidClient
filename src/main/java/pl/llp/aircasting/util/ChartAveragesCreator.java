package pl.llp.aircasting.util;

import com.github.mikephil.charting.data.Entry;
import com.google.common.collect.Lists;
import pl.llp.aircasting.android.Logger;
import pl.llp.aircasting.model.Measurement;
import pl.llp.aircasting.model.MeasurementStream;
import pl.llp.aircasting.model.Session;
import pl.llp.aircasting.view.presenter.MeasurementAggregator;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by radek on 27/02/18.
 */
public class ChartAveragesCreator {
    private final static int INTERVAL_IN_SECONDS = 60;
    private final static int MINUTES_IN_HOUR = 60;
    private final static int MAX_AVERAGES_AMOUNT = 9;
    private final static int MAX_X_VALUE = 8;
    private final static double MOBILE_FREQUENCY_DIVISOR = 8 * 1000;
    private final static double FIXED_FREQUENCY_DIVISOR = 8 * 1000 * 60;

    public static List getMobileEntries(MeasurementStream stream) {
        List<List<Measurement>> periodData;
        double streamFrequency = stream.getFrequency(MOBILE_FREQUENCY_DIVISOR);
        double xValue = MAX_X_VALUE;
        int measurementsInPeriod = (int) (INTERVAL_IN_SECONDS / streamFrequency);

        Logger.w("frequency " + streamFrequency);
        Logger.w("measurements in period " + measurementsInPeriod);

        List entries = new CopyOnWriteArrayList();
        List<Measurement> measurements = stream.getMeasurementsForPeriod(MAX_AVERAGES_AMOUNT, MOBILE_FREQUENCY_DIVISOR, 0);
        periodData = new ArrayList(Lists.partition(measurements, (int) measurementsInPeriod));

        Logger.w("period data size " + periodData.size());
        if (periodData.size() > 0) {
            synchronized (periodData) {
                for (List<Measurement> dataChunk : Lists.reverse(periodData)) {
                    Logger.w("datachunk size " + dataChunk.size());
                    if (dataChunk.size() > measurementsInPeriod - getTolerance(measurementsInPeriod)) {
                        double yValue = getAverage(dataChunk);
                        entries.add(new Entry((float) xValue, (float) yValue));
                        xValue--;
                    }
                }
            }
        }

        if (entries.size() == 0) {
            return entries;
        }

        return entries;
    }

    public static List<Entry> getFixedEntries(Session session, MeasurementStream stream) {
        List<Measurement> measurements;
        double xValue = MAX_X_VALUE;
        List entries = new CopyOnWriteArrayList();
        List<List<Measurement>> periodData;

        double streamFrequency = stream.getFrequency(FIXED_FREQUENCY_DIVISOR);
        double measurementsInPeriod = INTERVAL_IN_SECONDS / streamFrequency;
        double maxMeasurementsAmount = MAX_AVERAGES_AMOUNT * measurementsInPeriod;

        if (stream.getMeasurementsCount() < maxMeasurementsAmount) {
            int offset = (int) (session.getEnd().getMinutes() / streamFrequency);
            measurements = stream.getMeasurementsForPeriod(MAX_AVERAGES_AMOUNT, FIXED_FREQUENCY_DIVISOR, offset);
        } else {
            measurements = stream.getMeasurementsForPeriod(MAX_AVERAGES_AMOUNT, FIXED_FREQUENCY_DIVISOR, 0);
        }

        periodData = getPeriodData(measurements, streamFrequency);

        if (measurements.isEmpty() || periodData.isEmpty()) {
            return entries;
        }

        if (periodData.size() > 0) {
            Logger.w("period data size " + periodData.size());
            synchronized (periodData) {
                for (List<Measurement> dataChunk : Lists.reverse(periodData)) {
                    double yValue = getAverage(dataChunk);
                    entries.add(new Entry((float) xValue, (float) yValue));
                    xValue--;
                }
            }
        }

        if (entries.size() == 0) {
            return entries;
        }

        return entries;
    }

    private static double getTolerance(double measurementsInPeriod) {
        return 0.1 * measurementsInPeriod;
    }

    private static double getAverage(List<Measurement> measurements) {
        MeasurementAggregator aggregator = new MeasurementAggregator();

        for (int i = 0; i < measurements.size(); i++) {
            aggregator.add(measurements.get(i));
        }

        return Math.round(aggregator.getAverage().getValue());
    }

    private static List<List<Measurement>> getPeriodData(List<Measurement> measurements, double streamFrequency) {
        int firstMeasurementIndex = 0;
        List<List<Measurement>> result = new ArrayList<List<Measurement>>();
        Calendar calendar = GregorianCalendar.getInstance();

        Logger.w("measurements count " + measurements.size());
        Logger.w("frequency " + streamFrequency);

        if (measurements.isEmpty()) {
            return result;
        }

        for (int i = 0; i < MAX_AVERAGES_AMOUNT; i++) {
            Logger.w("first index " + firstMeasurementIndex);

            if (measurements.size() > firstMeasurementIndex) {
                int cutoff;
                List<Measurement> measurementsFromHour;
                Date firstMeasurementTime = measurements.get(firstMeasurementIndex).getTime();
                calendar.setTime(firstMeasurementTime);
                double minutes = calendar.get(Calendar.MINUTE);
                if (i == 0) {
                    cutoff = (int) ((MINUTES_IN_HOUR - minutes) / streamFrequency);
                } else {
                    cutoff = (int) (MINUTES_IN_HOUR / streamFrequency);
                }
                int lastMeasurementIndex = firstMeasurementIndex + cutoff;
                Logger.w("cutoff " + cutoff);
                Logger.w("last meas index " + lastMeasurementIndex);

                try {
                    measurementsFromHour = measurements.subList(firstMeasurementIndex, lastMeasurementIndex);
                } catch (IndexOutOfBoundsException e) {
                    return result;
                }

                if (measurementsFromHour.size() > 0) {
                    int firstMeasurementHour = calendar.get(Calendar.HOUR_OF_DAY);
                    int measurementsInHourCount = measurementsFromHour.size();

                    Date lastMeasurementTime = measurementsFromHour.get(measurementsInHourCount - 1).getTime();
                    calendar.setTime(lastMeasurementTime);
                    int lastMeasurementHour = calendar.get(Calendar.HOUR_OF_DAY);
                    int lastMeasurementMinutes = calendar.get(Calendar.MINUTE);
                    double cutoffOffset = 0;

                    Logger.w("meas in hour cout " + measurementsInHourCount);
                    Logger.w("first meas hour " + firstMeasurementHour);
                    Logger.w("last meas hour " + lastMeasurementHour);
                    Logger.w("first meas minutes " + minutes);
                    Logger.w("last meas minutes " + lastMeasurementMinutes);

                    if (isAcceptableValue(lastMeasurementMinutes)) {
                        Logger.w("acceptable");
                        result.add(measurementsFromHour);
                    } else {
                        cutoffOffset = getCutoffOffset(firstMeasurementHour, lastMeasurementHour, lastMeasurementMinutes, streamFrequency);
                        cutoff = (int) (cutoff + cutoffOffset);

                        Logger.w("cutoff offset " + cutoffOffset);

                        try {
                            measurementsFromHour = measurements.subList(firstMeasurementIndex, firstMeasurementIndex + cutoff);
                            result.add(measurementsFromHour);
                        } catch (IndexOutOfBoundsException e) {
                            return result;
                        }

                    }

                    firstMeasurementIndex = (int) (lastMeasurementIndex + cutoffOffset);

                } else {
                    return result;
                }
            }
        }

        return result;
    }

    private static double getCutoffOffset(int firstMeasurementHour, int lastMeasurementHour, int lastMeasurementMinutes, double streamFrequency) {
        if (firstMeasurementHour == lastMeasurementHour) {
            return (MINUTES_IN_HOUR - lastMeasurementMinutes) / streamFrequency;
        } else {
            return -lastMeasurementMinutes / streamFrequency;
        }
    }

    private static boolean isAcceptableValue(int lastMeasurementMinutes) {
        int[] acceptableValues = {59, 00, 01, 02};

        for (int value : acceptableValues) {
            if (lastMeasurementMinutes == value) {
                return true;
            }
        }

        return false;
    }
}
