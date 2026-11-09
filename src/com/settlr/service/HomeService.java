package com.settlr.service;

import com.settlr.exception.SettlrException;
import com.settlr.model.OwnedPet;
import com.settlr.model.RecurringBill;
import com.settlr.model.ShopCatalog;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.util.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Everything the Home page needs, in one place (Facade pattern).
 *
 * Home shows a bit of everything: tasks, swaps, money, bills, XP and the pet. Without this class
 * the Home screen would have to know seven services and how to combine them. Now it asks three
 * simple questions (getTasks, getMoney, getPet) and HomeService does the work behind them.
 * Each part loads on its own, so if one fails (e.g. the pet tables are missing) the rest still shows.
 */
public class HomeService {

    private final Session session = Session.getInstance();
    private final TaskService taskService = new TaskService();
    private final SwapService swapService = new SwapService();
    private final SettlementService settlementService = new SettlementService();
    private final HouseholdService householdService = new HouseholdService();
    private final PaymentInbox paymentInbox = new PaymentInbox();
    private final ShopService shopService = new ShopService();
    private final PetService petService = new PetService();

    // =====================================================================
    //  What Home shows
    // =====================================================================

    /** The left card: my overdue and due-today turns, swaps waiting for me, and my next turn. */
    public static final class TaskSummary {
        private final List<TaskAssignment> overdue;
        private final List<TaskAssignment> dueToday;
        private final List<SwapRequest> swaps;
        private final TaskAssignment next;

        TaskSummary(List<TaskAssignment> overdue, List<TaskAssignment> dueToday, List<SwapRequest> swaps,
                    TaskAssignment next) {
            this.overdue = overdue;
            this.dueToday = dueToday;
            this.swaps = swaps;
            this.next = next;
        }

        public List<TaskAssignment> getOverdue() { return overdue; }

        /** Still to do today, earliest first. */
        public List<TaskAssignment> getDueToday() { return dueToday; }

        /** Swap requests still waiting for my answer. */
        public List<SwapRequest> getSwaps() { return swaps; }

        /** My next turn after today, or null. Only looked up when nothing is overdue or due today. */
        public TaskAssignment getNext() { return next; }

        public boolean isAllCaughtUp() { return overdue.isEmpty() && dueToday.isEmpty(); }
    }

    /** The right card: what I owe or am owed overall, payments waiting for me, and the next bill. */
    public static final class MoneySummary {
        private final BigDecimal iOwe;
        private final BigDecimal owedToMe;
        private final List<String> peopleIOwe;
        private final List<String> peopleWhoOweMe;
        private final List<PendingPayment> waiting;
        private final RecurringBill nextBill;
        private final LocalDate nextBillDue;
        private final boolean iPayNextBill;

        MoneySummary(BigDecimal iOwe, BigDecimal owedToMe, List<String> peopleIOwe, List<String> peopleWhoOweMe,
                     List<PendingPayment> waiting, RecurringBill nextBill, LocalDate nextBillDue,
                     boolean iPayNextBill) {
            this.iOwe = iOwe;
            this.owedToMe = owedToMe;
            this.peopleIOwe = peopleIOwe;
            this.peopleWhoOweMe = peopleWhoOweMe;
            this.waiting = waiting;
            this.nextBill = nextBill;
            this.nextBillDue = nextBillDue;
            this.iPayNextBill = iPayNextBill;
        }

        /** Added up over everyone I owe (after netting with each person). */
        public BigDecimal getIOwe() { return iOwe; }

        public BigDecimal getOwedToMe() { return owedToMe; }

        public List<String> getPeopleIOwe() { return peopleIOwe; }

        public List<String> getPeopleWhoOweMe() { return peopleWhoOweMe; }

        /** Settle ups and single payments, both as PendingPayment (Adapter). */
        public List<PendingPayment> getWaiting() { return waiting; }

        /** The running bill I'm part of that's due soonest, or null. */
        public RecurringBill getNextBill() { return nextBill; }

        public LocalDate getNextBillDue() { return nextBillDue; }

        /** True if I'm the one who pays the next bill (then there's no "your share"). */
        public boolean isIPayNextBill() { return iPayNextBill; }
    }

    /** The middle: my XP, my pets, the one I show, and how it feels. */
    public static final class PetSummary {
        private final int xp;
        private final int xpThisWeek;
        private final List<OwnedPet> pets;
        private final OwnedPet shown;
        private final PetService.Mood mood;

        PetSummary(int xp, int xpThisWeek, List<OwnedPet> pets, OwnedPet shown, PetService.Mood mood) {
            this.xp = xp;
            this.xpThisWeek = xpThisWeek;
            this.pets = pets;
            this.shown = shown;
            this.mood = mood;
        }

        public int getXp() { return xp; }

        public int getXpThisWeek() { return xpThisWeek; }

        public List<OwnedPet> getPets() { return pets; }

        /** The pet on Home, or null when I have no pet yet. */
        public OwnedPet getShown() { return shown; }

        /** Null when I have no pet. */
        public PetService.Mood getMood() { return mood; }

        public boolean canAffordEgg() { return xp >= ShopCatalog.EGG_PRICE; }
    }

    // =====================================================================
    //  Reading
    // =====================================================================

    public TaskSummary getTasks() throws SettlrException {
        int myId = session.requireUser().getId();
        LocalDateTime now = LocalDateTime.now();

        List<TaskAssignment> overdue = new ArrayList<>();
        for (TaskAssignment a : taskService.getOverdue()) {
            if (a.getAssigneeId() == myId) {
                overdue.add(a);
            }
        }
        List<TaskAssignment> today = new ArrayList<>();
        for (TaskAssignment a : taskService.getMyTasksDueToday()) {
            if (a.getStatus() == TaskStatus.PENDING && !a.getDueAt().isBefore(now)) {
                today.add(a);
            }
        }
        today.sort(Comparator.comparing(TaskAssignment::getDueAt));

        List<SwapRequest> swaps = new ArrayList<>();
        for (SwapRequest s : swapService.getIncoming()) { // only the ones still waiting for an answer
            if (s.getStatus() == SwapStatus.PENDING && s.getAssignmentDueAt().isAfter(now)) {
                swaps.add(s);
            }
        }
        TaskAssignment next = overdue.isEmpty() && today.isEmpty() ? nextTurn(myId, now) : null;
        return new TaskSummary(overdue, today, swaps, next);
    }

    public MoneySummary getMoney() throws SettlrException {
        int myId = session.requireUser().getId();

        BigDecimal iOwe = BigDecimal.ZERO;
        BigDecimal owedToMe = BigDecimal.ZERO;
        List<String> peopleIOwe = new ArrayList<>();
        List<String> peopleWhoOweMe = new ArrayList<>();
        for (SettlementService.Person p : settlementService.getPeople()) {
            if (p.getNet().signum() < 0) {
                iOwe = iOwe.add(p.getNet().negate());
                peopleIOwe.add(p.getUser().getDisplayName());
            } else if (p.getNet().signum() > 0) {
                owedToMe = owedToMe.add(p.getNet());
                peopleWhoOweMe.add(p.getUser().getDisplayName());
            }
        }

        RecurringBill nextBill = null;
        LocalDate nextDue = null;
        LocalDate today = LocalDate.now();
        for (RecurringBill b : householdService.getBills()) {
            if (!b.isActive() || !b.getMemberIds().contains(myId)) {
                continue;
            }
            LocalDate due = b.nextDueDate(today);
            if (nextDue == null || due.isBefore(nextDue)) {
                nextBill = b;
                nextDue = due;
            }
        }
        return new MoneySummary(iOwe, owedToMe, peopleIOwe, peopleWhoOweMe, paymentInbox.getWaitingForMe(),
                nextBill, nextDue, nextBill != null && nextBill.getPaidBy() == myId);
    }

    public PetSummary getPet() throws SettlrException {
        int xp = shopService.getBalance();
        int thisWeek = petService.getXpThisWeek();
        List<OwnedPet> pets = petService.getMyPets();
        String shownCode = petService.getShownPetCode(pets);
        OwnedPet shown = null;
        for (OwnedPet p : pets) {
            if (p.getPet().getCode().equals(shownCode)) {
                shown = p;
            }
        }
        PetService.Mood mood = shown == null ? null : petService.getMood();
        return new PetSummary(xp, thisWeek, Collections.unmodifiableList(pets), shown, mood);
    }

    /** Codes of everything I own (for the Edit pet popup). */
    public Set<String> getOwnedCodes() throws SettlrException {
        return shopService.getOwnedCodes();
    }

    // =====================================================================
    //  Doing
    // =====================================================================

    /** Ticks off a turn. Returns true if it was done late. */
    public boolean completeTask(TaskAssignment a) throws SettlrException {
        boolean late = a.getDueAt().isBefore(LocalDateTime.now());
        taskService.completeAssignment(a.getId());
        return late;
    }

    public void answerSwap(SwapRequest s, boolean accept) throws SettlrException {
        if (accept) {
            swapService.accept(s.getId());
        } else {
            swapService.decline(s.getId());
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** My next pending turn after now (this week or next), including turns not created yet. */
    private TaskAssignment nextTurn(int myId, LocalDateTime now) throws SettlrException {
        TaskAssignment best = null;
        for (LocalDate week : new LocalDate[] {LocalDate.now(), LocalDate.now().plusDays(7)}) {
            List<TaskAssignment> all = new ArrayList<>(taskService.getWeek(week));
            all.addAll(taskService.getPlannedTurns(week));
            for (TaskAssignment a : all) {
                if (a.getAssigneeId() == myId && a.getStatus() == TaskStatus.PENDING && a.getDueAt().isAfter(now)
                        && (best == null || a.getDueAt().isBefore(best.getDueAt()))) {
                    best = a;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }
}
