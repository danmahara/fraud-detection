package com.fraud.detection.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fraud.detection.entity.enums.RiskLevel;

class RiskBlendTest {

    @Test
    void strongFloorGivesOrange() { // UT-22
        double f = TransactionService.blend(0.02, 0.50);
        assertEquals(0.90, f, 1e-9);
        assertEquals(RiskLevel.ORANGE, TransactionService.bandFor(f));
    }

    @Test
    void weakFloorGivesYellow() { // UT-23
        double f = TransactionService.blend(0.30, 0.30);
        assertEquals(0.55, f, 1e-9);
        assertEquals(RiskLevel.YELLOW, TransactionService.bandFor(f));
    }

    @Test
    void additionWithoutFloor() { // UT-24
        double f = TransactionService.blend(0.70, 0.30);
        assertEquals(0.85, f, 1e-9);
        assertEquals(RiskLevel.ORANGE, TransactionService.bandFor(f));
    }

    @Test
    void highMlNoContextGivesRed() { // UT-25
        double f = TransactionService.blend(0.97, 0.0);
        assertEquals(0.97, f, 1e-9);
        assertEquals(RiskLevel.RED, TransactionService.bandFor(f));
    }

    @Test
    void scoreIsCappedAtOne() { // UT-26
        assertEquals(1.0, TransactionService.blend(0.9, 0.5), 1e-9);
    }

    @Test
    void bandBoundaries() {
        assertEquals(RiskLevel.GREEN, TransactionService.bandFor(0.49));
        assertEquals(RiskLevel.YELLOW, TransactionService.bandFor(0.50));
        assertEquals(RiskLevel.ORANGE, TransactionService.bandFor(0.80));
        assertEquals(RiskLevel.RED, TransactionService.bandFor(0.95));
    }
}
