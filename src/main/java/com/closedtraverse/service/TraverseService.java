package com.closedtraverse.service;

import com.closedtraverse.api.TraverseModels.CreateRequest;
import com.closedtraverse.api.TraverseModels.LegRequest;
import com.closedtraverse.api.TraverseModels.TraverseResponse;
import com.closedtraverse.api.TraverseModels.TraverseSummary;
import com.closedtraverse.calculation.InvalidTraverseException;
import com.closedtraverse.calculation.TraverseAdjuster;
import com.closedtraverse.calculation.TraverseAdjuster.Adjustment;
import com.closedtraverse.calculation.TraverseAdjuster.Observation;
import com.closedtraverse.persistence.TraverseRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TraverseService {
    private final TraverseAdjuster adjuster;
    private final TraverseRepository repository;

    public TraverseService(TraverseAdjuster adjuster, TraverseRepository repository) {
        this.adjuster = adjuster;
        this.repository = repository;
    }

    @Transactional
    public UUID create(CreateRequest request) {
        if (request == null || request.timeZone() == null || request.timeZone().isBlank()
                || request.timeZone().length() > 100) {
            throw new InvalidTraverseException("timeZone must contain 1 to 100 characters.");
        }
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException ex) {
            throw new InvalidTraverseException("timeZone must be a recognized time zone.");
        }
        List<Observation> observations = request.legs() == null ? null : request.legs().stream()
                .map(this::toObservation).toList();
        Adjustment adjustment = adjuster.adjust(observations);
        UUID id = UUID.randomUUID();
        repository.save(id, request.timeZone(), Instant.now(), adjustment);
        return id;
    }

    private Observation toObservation(LegRequest leg) {
        return leg == null ? null : new Observation(leg.fromStationNo(), leg.toStationNo(),
                leg.distanceM(), leg.azimuthDeg());
    }

    public TraverseResponse get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TraverseNotFoundException(id));
    }

    public List<TraverseSummary> list() {
        return repository.findAll();
    }
}
