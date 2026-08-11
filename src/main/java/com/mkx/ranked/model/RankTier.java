package com.mkx.ranked.model;

public enum RankTier {
    S_TIER("S-Tier", "🥇", 1, 10),
    A_TIER("A-Tier", "🥈", 11, 20),
    B_TIER("B-Tier", "🥉", 21, 30),
    C_TIER("C-Tier", "🎗️", 31, 50),
    D_TIER("D-Tier", "🎮", 51, Integer.MAX_VALUE);

    private final String name;
    private final String emoji;
    private final int minRank;
    private final int maxRank;

    RankTier(String name, String emoji, int minRank, int maxRank) {
        this.name = name;
        this.emoji = emoji;
        this.minRank = minRank;
        this.maxRank = maxRank;
    }

    public static RankTier getTierByRank(int rank) {
        for (RankTier tier : values()) {
            if (rank >= tier.minRank && rank <= tier.maxRank) {
                return tier;
            }
        }
        return D_TIER;
    }

    public String getName() { return name; }
    public String getEmoji() { return emoji; }
}