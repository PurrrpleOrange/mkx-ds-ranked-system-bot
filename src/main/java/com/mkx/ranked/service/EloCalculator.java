package com.mkx.ranked.service;

public class EloCalculator {

    /**
     * Вычисляет математическое ожидание победы (E_A) для игрока A против B.
     */
    public static double calculateExpectedScore(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * Вычисляет фактическую ценность победы (S) на основе счета в FT5.
     * @param winnerScore Счёт победителя (обычно 5)
     * @param loserScore Счёт проигравшего (от 0 до 4)
     */
    public static double calculateActualScore(int winnerScore, int loserScore) {
        int diff = winnerScore - loserScore;
        return 0.5 + Math.sqrt(diff) * 0.2;
    }

    /**
     * Определяет K-фактор на основе количества уже сыгранных матчей.
     */
    public static int getKFactor(int gamesPlayed) {
        if (gamesPlayed < 5) {
            return 16;
        } else if (gamesPlayed < 10) {
            return 24;
        } else {
            return 32;
        }
    }

    /**
     * Главная формула расчета изменения рейтинга (Delta).
     * * @param ratingWinner Текущий MMR победителя
     * @param gamesWinner Сыграно матчей победителем
     * @param ratingLoser Текущий MMR проигравшего
     * @param winnerScore Счёт победителя
     * @param loserScore Счёт проигравшего
     * @return Массив из двух значений: [deltaWinner, deltaLoser]
     */
    public static int[] calculateRatingChange(int ratingWinner, int gamesWinner,
                                              int ratingLoser, int gamesLoser,
                                              int winnerScore, int loserScore) {
        // 1. Ожидание
        double expectedWinner = calculateExpectedScore(ratingWinner, ratingLoser);
        double expectedLoser = 1.0 - expectedWinner;

        // 2. Фактический результат FT5
        double scoreWinner = calculateActualScore(winnerScore, loserScore);
        double scoreLoser = 1.0 - scoreWinner;

        // 3. Динамический K-фактор
        int kWinner = getKFactor(gamesWinner);
        int kLoser = getKFactor(gamesLoser);

        // 4. Формула Delta с усилителем (1 + |S - E|)
        double rawDeltaWinner = kWinner * (scoreWinner - expectedWinner) * (1.0 + Math.abs(scoreWinner - expectedWinner));
        double rawDeltaLoser = kLoser * (scoreLoser - expectedLoser) * (1.0 + Math.abs(scoreLoser - expectedLoser));

        // 5. Округление (победитель получает +, проигравший теряет -)
        int deltaWinner = (int) Math.round(rawDeltaWinner);
        int deltaLoser = (int) Math.round(rawDeltaLoser);

        return new int[]{deltaWinner, deltaLoser};
    }
}