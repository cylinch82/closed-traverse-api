package com.closedtraverse.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TraverseModels {
    private TraverseModels() {}

    public record CreateRequest(String timeZone, List<LegRequest> legs) {}

    public record LegRequest(String fromStationNo, String toStationNo, Double distanceM,
                             Double azimuthDeg) {}

    public record CreatedResponse(UUID id) {}

    public record TraverseSummary(UUID id, String timeZone, Instant createdAt,
                                  String adjustmentVersion, double totalLengthM,
                                  double closureDxM, double closureDyM) {}

    public record TraverseResponse(UUID id, String timeZone, Instant createdAt,
                                   String adjustmentVersion, double totalLengthM,
                                   double closureDxM, double closureDyM,
                                   List<LegResponse> legs) {}

    public record LegResponse(int sequenceNo, String fromStationNo, String toStationNo,
                              double distanceM, double azimuthDeg, double rawDxM, double rawDyM,
                              double correctionDxM, double correctionDyM, double adjustedDxM,
                              double adjustedDyM, double adjustedEndXM, double adjustedEndYM) {}

    public record ErrorResponse(String code, String message) {}
}
