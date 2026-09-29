package com.closedtraverse.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.closedtraverse.calculation.TraverseAdjuster.Adjustment;
import com.closedtraverse.calculation.TraverseAdjuster.Observation;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraverseAdjusterTest {
    private final TraverseAdjuster adjuster = new TraverseAdjuster();

    @Test
    void exampleAdjustmentPreservesObservationsAndCloses() {
        List<Observation> original = example();
        Adjustment result = adjuster.adjust(original);

        assertEquals(99.986, result.totalLengthM(), 1e-10);
        assertEquals(0.016501, result.closureDxM(), 1e-6);
        assertEquals(0.015769, result.closureDyM(), 1e-6);
        assertEquals(0.022824, Math.hypot(result.closureDxM(), result.closureDyM()), 1e-6);
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i).distanceM(), result.legs().get(i).distanceM());
            assertEquals(original.get(i).azimuthDeg(), result.legs().get(i).azimuthDeg());
        }
        assertEquals(29.999046, result.legs().getFirst().adjustedEndXM(), 1e-6);
        assertEquals(0.005741, result.legs().getFirst().adjustedEndYM(), 1e-6);
        assertEquals(-result.closureDxM(),
                result.legs().stream().mapToDouble(TraverseAdjuster.AdjustedLeg::correctionDxM).sum(), 1e-12);
        assertEquals(-result.closureDyM(),
                result.legs().stream().mapToDouble(TraverseAdjuster.AdjustedLeg::correctionDyM).sum(), 1e-12);
        assertEquals(0, result.legs().getLast().adjustedEndXM(), 1e-12);
        assertEquals(0, result.legs().getLast().adjustedEndYM(), 1e-12);
    }

    @Test
    void rejectsInvalidInputAndBrokenRoutes() {
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(example().subList(0, 2)));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(List.of(
                new Observation("S1", "S2", 1.0, 0.0),
                new Observation("S3", "S4", 1.0, 90.0),
                new Observation("S4", "S1", 1.0, 180.0))));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(replaceFirst(0.0, 90.0)));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(replaceFirst(Double.NaN, 90.0)));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(replaceFirst(1.0, 360.0)));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(replaceFirst(1.0, Double.POSITIVE_INFINITY)));
        List<Observation> longStation = new java.util.ArrayList<>(example());
        longStation.set(0, new Observation("S".repeat(101), "S2", 30.004, 89.98));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(longStation));
        assertThrows(InvalidTraverseException.class, () -> adjuster.adjust(List.of(
                new Observation("S1", "S2", Double.MAX_VALUE, 90.0),
                new Observation("S2", "S3", Double.MAX_VALUE, 0.0),
                new Observation("S3", "S1", 1.0, 180.0))));
    }

    @Test
    void usesNorthClockwiseAzimuth() {
        Adjustment result = adjuster.adjust(List.of(
                new Observation("A", "B", 10.0, 90.0),
                new Observation("B", "C", 10.0, 0.0),
                new Observation("C", "D", 10.0, 270.0),
                new Observation("D", "A", 10.0, 180.0)));
        assertEquals(10, result.legs().getFirst().rawDxM(), 1e-12);
        assertEquals(0, result.legs().getFirst().rawDyM(), 1e-12);
        assertEquals(0, result.legs().get(1).rawDxM(), 1e-12);
        assertEquals(10, result.legs().get(1).rawDyM(), 1e-12);
        assertTrue(Math.hypot(result.closureDxM(), result.closureDyM()) < 1e-12);
    }

    private List<Observation> replaceFirst(double distance, double azimuth) {
        List<Observation> legs = new java.util.ArrayList<>(example());
        legs.set(0, new Observation("S1", "S2", distance, azimuth));
        return legs;
    }

    static List<Observation> example() {
        return List.of(new Observation("S1", "S2", 30.004, 89.98),
                new Observation("S2", "S3", 20.006, 0.03),
                new Observation("S3", "S4", 29.991, 269.97),
                new Observation("S4", "S1", 19.985, 180.02));
    }
}
