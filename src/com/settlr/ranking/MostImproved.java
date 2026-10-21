package com.settlr.ranking;

import com.settlr.exception.DataAccessException;
import com.settlr.model.LeaderboardEntry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Most Improved: Settlr Score of this period minus the score of the previous one
 * (this week minus last week, or this month minus last month).
 *
 * <p>Why it HAS-A SettlrScore instead of copying it (composition): the score formula and its
 * weights then stay in exactly one class. This ranking is hidden on All Time because "all time"
 * has no previous period to compare with.
 */
public class MostImproved extends RankingCategory {

    private final SettlrScore scorer = new SettlrScore();

    public MostImproved() {
        super("Most Improved", "Biggest jump in Settlr Score compared with the last period. Level up!", false);
    }

    @Override
    public boolean isAvailableFor(RankingPeriod period) {
        return period != RankingPeriod.ALL_TIME;
    }

    @Override
    public List<LeaderboardEntry> compute(int householdId, RankingPeriod period) throws DataAccessException {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (!isAvailableFor(period)) {
            return entries; // nothing to compare on All Time
        }

        LocalDate today = LocalDate.now();
        LocalDate lastPeriodDay = period.dayInPreviousPeriod(today);

        Map<Integer, Integer> before = new HashMap<>();
        for (SettlrScore.MemberScore m : scorer.scoresBetween(
                householdId, period.getStart(lastPeriodDay), period.getEnd(lastPeriodDay))) {
            before.put(m.userId(), m.score());
        }

        for (SettlrScore.MemberScore m : scorer.scoresBetween(
                householdId, period.getStart(today), period.getEnd(today))) {
            int change = m.score() - before.getOrDefault(m.userId(), 0);
            entries.add(new LeaderboardEntry(m.userId(), m.displayName(), change, 1, formatValue(change)));
        }
        assignRanks(entries);
        return entries;
    }

    /** "+7 pts", "-1 pt", "0 pts": the sign matters because the change can be negative. */
    private String formatValue(double v) {
        long n = Math.round(v);
        return (n > 0 ? "+" : "") + n + (Math.abs(n) == 1 ? " pt" : " pts");
    }
}
