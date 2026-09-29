package com.closedtraverse.calculation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TraverseAdjuster {
    public static final String VERSION = "LENGTH_PROPORTIONAL_V1";

    public Adjustment adjust(List<Observation> observations) {
        if (observations == null || observations.size() < 3) {
            throw new InvalidTraverseException("At least three legs are required.");
        }

        double totalLengthM = 0;
        double closureDxM = 0;
        double closureDyM = 0;
        List<RawLeg> rawLegs = new ArrayList<>(observations.size());

        for (int index = 0; index < observations.size(); index++) {
            Observation leg = observations.get(index);
            if (leg == null || leg.fromStationNo() == null || leg.fromStationNo().isBlank()
                    || leg.toStationNo() == null || leg.toStationNo().isBlank()
                    || leg.fromStationNo().length() > 100 || leg.toStationNo().length() > 100) {
                throw new InvalidTraverseException("Each station number must contain 1 to 100 characters.");
            }
            if (leg.distanceM() == null || !Double.isFinite(leg.distanceM()) || leg.distanceM() <= 0) {
                throw new InvalidTraverseException("Each distanceM must be a positive finite number.");
            }
            if (leg.azimuthDeg() == null || !Double.isFinite(leg.azimuthDeg())
                    || leg.azimuthDeg() < 0 || leg.azimuthDeg() >= 360) {
                throw new InvalidTraverseException("Each azimuthDeg must be finite and in [0, 360).");
            }
            Observation next = observations.get((index + 1) % observations.size());
            if (next == null || !Objects.equals(leg.toStationNo(), next.fromStationNo())) {
                throw new InvalidTraverseException("Legs must connect in order and return to the first station.");
            }

            double radians = Math.toRadians(leg.azimuthDeg());
            double rawDxM = leg.distanceM() * Math.sin(radians);
            double rawDyM = leg.distanceM() * Math.cos(radians);
            totalLengthM += leg.distanceM();
            closureDxM += rawDxM;
            closureDyM += rawDyM;
            if (!Double.isFinite(totalLengthM) || !Double.isFinite(closureDxM)
                    || !Double.isFinite(closureDyM)) {
                throw new InvalidTraverseException("The observation values exceed the calculation range.");
            }
            rawLegs.add(new RawLeg(leg, rawDxM, rawDyM));
        }

        List<AdjustedLeg> adjustedLegs = new ArrayList<>(rawLegs.size());
        double x = 0;
        double y = 0;
        for (int index = 0; index < rawLegs.size(); index++) {
            RawLeg raw = rawLegs.get(index);
            double weight = raw.observation().distanceM() / totalLengthM;
            double correctionDxM = -closureDxM * weight;
            double correctionDyM = -closureDyM * weight;
            double adjustedDxM = raw.dxM() + correctionDxM;
            double adjustedDyM = raw.dyM() + correctionDyM;
            x += adjustedDxM;
            y += adjustedDyM;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new InvalidTraverseException("The observation values exceed the calculation range.");
            }
            adjustedLegs.add(new AdjustedLeg(index + 1, raw.observation().fromStationNo(),
                    raw.observation().toStationNo(), raw.observation().distanceM(),
                    raw.observation().azimuthDeg(), raw.dxM(), raw.dyM(), correctionDxM,
                    correctionDyM, adjustedDxM, adjustedDyM, x, y));
        }
        return new Adjustment(totalLengthM, closureDxM, closureDyM, List.copyOf(adjustedLegs));
    }

    public record Observation(String fromStationNo, String toStationNo, Double distanceM, Double azimuthDeg) {}

    private record RawLeg(Observation observation, double dxM, double dyM) {}

    public record Adjustment(double totalLengthM, double closureDxM, double closureDyM,
                             List<AdjustedLeg> legs) {}

    public record AdjustedLeg(int sequenceNo, String fromStationNo, String toStationNo,
                              double distanceM, double azimuthDeg, double rawDxM, double rawDyM,
                              double correctionDxM, double correctionDyM, double adjustedDxM,
                              double adjustedDyM, double adjustedEndXM, double adjustedEndYM) {}
}
