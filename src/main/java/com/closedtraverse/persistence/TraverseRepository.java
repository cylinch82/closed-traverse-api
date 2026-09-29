package com.closedtraverse.persistence;

import com.closedtraverse.api.TraverseModels.LegResponse;
import com.closedtraverse.api.TraverseModels.TraverseResponse;
import com.closedtraverse.api.TraverseModels.TraverseSummary;
import com.closedtraverse.calculation.TraverseAdjuster;
import com.closedtraverse.calculation.TraverseAdjuster.AdjustedLeg;
import com.closedtraverse.calculation.TraverseAdjuster.Adjustment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TraverseRepository {
    private final JdbcTemplate jdbc;

    public TraverseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UUID id, String timeZone, Instant createdAt, Adjustment adjustment) {
        jdbc.update("""
                INSERT INTO traverse (id, time_zone, created_at, adjustment_version,
                                      total_length_m, closure_dx_m, closure_dy_m)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, timeZone, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                TraverseAdjuster.VERSION, adjustment.totalLengthM(), adjustment.closureDxM(),
                adjustment.closureDyM());

        for (AdjustedLeg leg : adjustment.legs()) {
            jdbc.update("""
                    INSERT INTO traverse_leg (traverse_id, sequence_no, from_station_no,
                        to_station_no, observed_distance_m, observed_azimuth_deg,
                        raw_dx_m, raw_dy_m, correction_dx_m, correction_dy_m,
                        adjusted_dx_m, adjusted_dy_m, adjusted_end_x_m, adjusted_end_y_m)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, leg.sequenceNo(), leg.fromStationNo(), leg.toStationNo(),
                    leg.distanceM(), leg.azimuthDeg(), leg.rawDxM(), leg.rawDyM(),
                    leg.correctionDxM(), leg.correctionDyM(), leg.adjustedDxM(),
                    leg.adjustedDyM(), leg.adjustedEndXM(), leg.adjustedEndYM());
        }
    }

    public Optional<TraverseResponse> findById(UUID id) {
        List<TraverseSummary> matches = jdbc.query("""
                SELECT id, time_zone, created_at, adjustment_version, total_length_m,
                       closure_dx_m, closure_dy_m
                FROM traverse WHERE id = ?
                """, summaryMapper(), id);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        TraverseSummary header = matches.getFirst();
        List<LegResponse> legs = jdbc.query("""
                SELECT sequence_no, from_station_no, to_station_no, observed_distance_m,
                       observed_azimuth_deg, raw_dx_m, raw_dy_m, correction_dx_m,
                       correction_dy_m, adjusted_dx_m, adjusted_dy_m, adjusted_end_x_m,
                       adjusted_end_y_m
                FROM traverse_leg WHERE traverse_id = ? ORDER BY sequence_no
                """, (rs, rowNum) -> new LegResponse(rs.getInt("sequence_no"),
                rs.getString("from_station_no"), rs.getString("to_station_no"),
                rs.getDouble("observed_distance_m"), rs.getDouble("observed_azimuth_deg"),
                rs.getDouble("raw_dx_m"), rs.getDouble("raw_dy_m"),
                rs.getDouble("correction_dx_m"), rs.getDouble("correction_dy_m"),
                rs.getDouble("adjusted_dx_m"), rs.getDouble("adjusted_dy_m"),
                rs.getDouble("adjusted_end_x_m"), rs.getDouble("adjusted_end_y_m")), id);
        return Optional.of(new TraverseResponse(header.id(), header.timeZone(), header.createdAt(),
                header.adjustmentVersion(), header.totalLengthM(), header.closureDxM(),
                header.closureDyM(), legs));
    }

    public List<TraverseSummary> findAll() {
        return jdbc.query("""
                SELECT id, time_zone, created_at, adjustment_version, total_length_m,
                       closure_dx_m, closure_dy_m
                FROM traverse ORDER BY created_at DESC, id DESC
                """, summaryMapper());
    }

    private RowMapper<TraverseSummary> summaryMapper() {
        return (rs, rowNum) -> mapSummary(rs);
    }

    private TraverseSummary mapSummary(ResultSet rs) throws SQLException {
        return new TraverseSummary(rs.getObject("id", UUID.class), rs.getString("time_zone"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getString("adjustment_version"), rs.getDouble("total_length_m"),
                rs.getDouble("closure_dx_m"), rs.getDouble("closure_dy_m"));
    }
}
