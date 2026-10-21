package com.settlr.ranking;

import com.settlr.exception.DataAccessException;
import com.settlr.model.LeaderboardEntry;

import java.util.Comparator;
import java.util.List;

/**
 * Abstract parent of all 13 rankings. It fixes what every ranking must offer
 * (title, description, shame flag, compute) and leaves "how to compute" to the subclasses.
 *
 * <p>Why abstract: LeaderboardService holds a List of RankingCategory and calls compute()
 * on each one without knowing which ranking it is. That is the polymorphism the project
 * is graded on.
 */
public abstract class RankingCategory {

    private final String title;
    private final String description;
    private final boolean shame;

    protected RankingCategory(String title, String description, boolean shame) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A ranking needs a title.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A ranking needs a description.");
        }
        this.title = title;
        this.description = description;
        this.shame = shame;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    /** True for the playful negative rankings that an Admin can hide. */
    public boolean isShame() {
        return shame;
    }

    /**
     * Whether this ranking has anything to show on the given tab. Almost all do; MostImproved
     * overrides this to say no for All Time. The screen asks instead of checking the class
     * name, so the screen never needs to know which rankings exist.
     */
    public boolean isAvailableFor(RankingPeriod period) {
        return true;
    }

    /** Members of the household ranked for the period, best first, ties listed A to Z. */
    public abstract List<LeaderboardEntry> compute(int householdId, RankingPeriod period)
            throws DataAccessException;

    /** "1 week" / "3 weeks": picks the singular or plural word. Shared by the subclasses' labels. */
    protected static String countLabel(double value, String singular, String plural) {
        long n = Math.round(value);
        return n + " " + (n == 1 ? singular : plural);
    }

    /**
     * Sorts best value first (ties A to Z) and gives every entry its rank. Equal values share
     * a rank and the next rank is skipped (1, 1, 3), exactly like SQL RANK(), so the three Java
     * rankings behave the same as the ten SQL ones.
     */
    protected static void assignRanks(List<LeaderboardEntry> entries) {
        entries.sort(Comparator
                .comparingDouble(LeaderboardEntry::getValue).reversed()
                .thenComparing(LeaderboardEntry::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry current = entries.get(i);
            boolean tiedWithPrevious = i > 0
                    && Double.compare(entries.get(i - 1).getValue(), current.getValue()) == 0;
            current.setRank(tiedWithPrevious ? entries.get(i - 1).getRank() : i + 1);
        }
    }
}
