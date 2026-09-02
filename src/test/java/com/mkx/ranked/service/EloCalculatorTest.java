package com.mkx.ranked.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloCalculatorTest {

    @Test
    void equalRatingsProduceOppositeDeltasWithEqualExperience() {
        EloCalculator.RatingChange change = EloCalculator.calculate(1000, 0, 1000, 0, 5, 4);

        assertEquals(4, change.deltaWinner());
        assertEquals(-4, change.deltaLoser());
    }

    @Test
    void favoriteStillGainsAndLoserStillLosesAfterFavoriteWins() {
        EloCalculator.RatingChange change = EloCalculator.calculate(2000, 10, 1000, 10, 5, 4);

        assertTrue(change.deltaWinner() > 0);
        assertTrue(change.deltaLoser() < 0);
    }

    @Test
    void underdogVictoryProducesLargerPositiveChange() {
        EloCalculator.RatingChange favoriteWin = EloCalculator.calculate(1400, 10, 1000, 10, 5, 0);
        EloCalculator.RatingChange underdogWin = EloCalculator.calculate(1000, 10, 1400, 10, 5, 0);

        assertTrue(underdogWin.deltaWinner() > favoriteWin.deltaWinner());
        assertTrue(underdogWin.deltaWinner() > 0);
        assertTrue(underdogWin.deltaLoser() < 0);
    }

    @Test
    void stronglyDifferentRatingsUseStableExpectedScoreBounds() {
        assertTrue(EloCalculator.calculateExpectedScore(2000, 1000) > 0.99);
        assertTrue(EloCalculator.calculateExpectedScore(1000, 2000) < 0.01);
        assertEquals(
                1.0,
                EloCalculator.calculateExpectedScore(2000, 1000)
                        + EloCalculator.calculateExpectedScore(1000, 2000),
                1.0e-12
        );
    }

    @Test
    void gamesPlayedChangesKFactorAndMagnitude() {
        EloCalculator.RatingChange newcomers = EloCalculator.calculate(1000, 0, 1000, 0, 5, 0);
        EloCalculator.RatingChange experienced = EloCalculator.calculate(1000, 10, 1000, 10, 5, 0);

        assertEquals(16, EloCalculator.getKFactor(0));
        assertEquals(24, EloCalculator.getKFactor(5));
        assertEquals(32, EloCalculator.getKFactor(10));
        assertTrue(experienced.deltaWinner() > newcomers.deltaWinner());
        assertTrue(Math.abs(experienced.deltaLoser()) > Math.abs(newcomers.deltaLoser()));
    }

    @Test
    void differentExperienceCanProduceAsymmetricDeltas() {
        EloCalculator.RatingChange change = EloCalculator.calculate(1000, 0, 1000, 10, 5, 0);

        assertEquals(10, change.deltaWinner());
        assertEquals(-21, change.deltaLoser());
    }

    @Test
    void legacyArrayApiMatchesValueObjectApi() {
        EloCalculator.RatingChange expected = EloCalculator.calculate(1100, 4, 900, 7, 5, 2);

        assertEquals(
                java.util.List.of(expected.deltaWinner(), expected.deltaLoser()),
                java.util.Arrays.stream(EloCalculator.calculateRatingChange(1100, 4, 900, 7, 5, 2))
                        .boxed()
                        .toList()
        );
    }

    @Test
    void calculatorIsPureStaticJavaUtility() {
        assertFalse(EloCalculator.class.isAnnotationPresent(org.springframework.stereotype.Component.class));
        assertFalse(EloCalculator.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertTrue(java.util.Arrays.stream(EloCalculator.class.getDeclaredMethods())
                .allMatch(method -> Modifier.isStatic(method.getModifiers())));
        Constructor<?> constructor = EloCalculator.class.getDeclaredConstructors()[0];
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
}
