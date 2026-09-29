package com.closedtraverse.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.closedtraverse.calculation.TraverseAdjuster;
import com.closedtraverse.calculation.TraverseAdjuster.AdjustedLeg;
import com.closedtraverse.calculation.TraverseAdjuster.Adjustment;
import com.closedtraverse.calculation.TraverseAdjuster.Observation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class TraverseTransactionTest {
    @Autowired TraverseRepository repository;
    @Autowired TraverseAdjuster adjuster;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void failedLegInsertRollsBackHeaderAndEarlierLegs() {
        Adjustment valid = adjuster.adjust(List.of(
                new Observation("A", "B", 10.0, 90.0),
                new Observation("B", "C", 10.0, 0.0),
                new Observation("C", "A", 14.0, 225.0)));
        List<AdjustedLeg> duplicateSequence = new ArrayList<>(valid.legs());
        AdjustedLeg second = duplicateSequence.get(1);
        duplicateSequence.set(1, new AdjustedLeg(1, second.fromStationNo(), second.toStationNo(),
                second.distanceM(), second.azimuthDeg(), second.rawDxM(), second.rawDyM(),
                second.correctionDxM(), second.correctionDyM(), second.adjustedDxM(),
                second.adjustedDyM(), second.adjustedEndXM(), second.adjustedEndYM()));
        Adjustment invalid = new Adjustment(valid.totalLengthM(), valid.closureDxM(),
                valid.closureDyM(), duplicateSequence);
        UUID id = UUID.randomUUID();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(DataIntegrityViolationException.class, () -> transaction.execute(status -> {
            repository.save(id, "Asia/Taipei", Instant.now(), invalid);
            return null;
        }));
        assertTrue(repository.findById(id).isEmpty());
    }
}
