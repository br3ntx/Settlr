package com.settlr.service;

import com.settlr.exception.AuthException;
import com.settlr.exception.NotFoundException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.LeaderboardEntry;
import com.settlr.ranking.BayadAgad;
import com.settlr.ranking.ChoreChampion;
import com.settlr.ranking.Dodger;
import com.settlr.ranking.EarlyBird;
import com.settlr.ranking.Ghost;
import com.settlr.ranking.Hero;
import com.settlr.ranking.MamayaNa;
import com.settlr.ranking.MostImproved;
import com.settlr.ranking.RankingCategory;
import com.settlr.ranking.RankingPeriod;
import com.settlr.ranking.SettlrScore;
import com.settlr.ranking.SlowSettler;
import com.settlr.ranking.Sponsor;
import com.settlr.ranking.Streak;
import com.settlr.ranking.UtangKing;
import com.settlr.util.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the leaderboard for the logged-in user's household.
 *
 * <p>POLYMORPHISM lives here: the service keeps a List of RankingCategory and calls compute()
 * on whichever one it is given. It never checks "is this ChoreChampion or Ghost?". Adding a
 * 14th ranking means writing one class and adding it to the list below, with no other change.
 */
public class LeaderboardService {

    /** In the order of the master doc's table in section 4. */
    private final List<RankingCategory> allCategories = List.of(
            new SettlrScore(),
            new ChoreChampion(),
            new EarlyBird(),
            new BayadAgad(),
            new Sponsor(),
            new Hero(),
            new Streak(),
            new MostImproved(),
            new MamayaNa(),
            new Ghost(),
            new UtangKing(),
            new SlowSettler(),
            new Dodger());

    /**
     * All 13 rankings, with no household or login needed. Added for screen previews and tests;
     * real screens use getCategories() so the "hide shame rankings" setting is respected.
     */
    public List<RankingCategory> getAllCategories() {
        return allCategories;
    }

    /** The rankings to show: all 13, minus the 5 shame ones when the household has hidden them. */
    public List<RankingCategory> getCategories() throws SettlrException {
        Household household = requireHousehold();
        List<RankingCategory> visible = new ArrayList<>();
        for (RankingCategory category : allCategories) {
            if (!(category.isShame() && household.isHideShameRanks())) {
                visible.add(category);
            }
        }
        return visible;
    }

    /** One ranking for one tab, best first. Hidden shame rankings return an empty list. */
    public List<LeaderboardEntry> getLeaderboard(RankingCategory category, RankingPeriod period)
            throws SettlrException {
        Household household = requireHousehold();
        if (category.isShame() && household.isHideShameRanks()) {
            return new ArrayList<>(); // a stale screen asked for a ranking the Admin has since hidden
        }
        return category.compute(household.getId(), period); // polymorphic call
    }

    /**
     * Weekly titles: user id -> titles of the rankings they lead on the This Week tab.
     * Ties for first all get the title. A title needs a top value above 0, so a ranking
     * where nobody has done anything yet crowns nobody.
     */
    public Map<Integer, List<String>> getWeeklyTitles() throws SettlrException {
        Household household = requireHousehold();
        Map<Integer, List<String>> titles = new HashMap<>();

        for (RankingCategory category : getCategories()) {
            if (!category.isAvailableFor(RankingPeriod.WEEK)) {
                continue;
            }
            List<LeaderboardEntry> entries = category.compute(household.getId(), RankingPeriod.WEEK);
            if (entries.isEmpty() || entries.get(0).getValue() <= 0) {
                continue;
            }
            for (LeaderboardEntry entry : entries) {
                if (entry.getRank() != 1) {
                    break; // entries are sorted, so the first non-#1 ends the leaders
                }
                titles.computeIfAbsent(entry.getUserId(), id -> new ArrayList<>()).add(category.getTitle());
            }
        }
        return titles;
    }

    private Household requireHousehold() throws SettlrException {
        Session session = Session.getInstance();
        if (!session.isLoggedIn()) {
            throw new AuthException("Please log in first.");
        }
        Household household = session.getCurrentHousehold();
        if (household == null) {
            throw new NotFoundException("Join or create a household to see the leaderboard.");
        }
        return household;
    }
}
