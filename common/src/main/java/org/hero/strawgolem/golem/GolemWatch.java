package org.hero.strawgolem.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.Constants;

/**
 * Permanent, near-silent instrumentation for stuck golems.
 *
 * <p>Every freeze we have chased so far ("standing holding essence", "orbiting
 * the chest", "hung on the eat goal", "broke free at sunset") looked identical
 * from the outside and had a completely different cause underneath. Screenshots
 * could not tell them apart; a log line did, every single time. This keeps that
 * instrument permanently installed instead of bolting it on after each new
 * report and ripping it out again.
 *
 * <p>The design point is <b>cheap to watch, expensive only when it fires</b>:
 *
 * <ul>
 *   <li>Detection is a position compare once a second - a couple of
 *       subtractions per golem. Nothing is allocated, nothing is scanned.</li>
 *   <li>A golem must be motionless for {@link #STUCK_AFTER} ticks (30s) before
 *       anything is written, so a healthy crew logs <i>nothing at all</i>. A
 *       golem legitimately standing still to craft or eat clears that bar only
 *       if it is genuinely wedged.</li>
 *   <li>Because a report is rare, it can afford to be thorough - it asks the
 *       deliverer for a live target and dumps the full goal/nav picture. That
 *       one line is what actually identifies the bug.</li>
 *   <li>Exactly ONE line per episode, plus one when it resolves. No repeating
 *       spam while a golem stays stuck - the old diagnostic wrote every 10s and
 *       produced 227 lines in a session.</li>
 * </ul>
 */
public final class GolemWatch {
    /** How often to compare position, in ticks. */
    public static final int SAMPLE_INTERVAL = 20;
    /** Motionless this long (ticks) before we call it stuck. 30s at 20tps. */
    public static final int STUCK_AFTER = 600;
    /** Movement under this (squared blocks) between samples counts as motionless. */
    public static final double MOVE_EPS_SQ = 0.04; // 0.2 blocks

    private GolemWatch() {}

    /**
     * The one-shot deep report. Called only when a golem crosses the stuck
     * threshold, so it is allowed to do real work gathering context.
     */
    static void reportStuck(StrawGolem golem, int stuckTicks) {
        // goalSelector is protected on Mob, so the golem hands us the names.
        // "NONE-DEADLOCK" is the nastiest failure mode we have hit: no goal is
        // running at all, so nothing will ever move the golem again on its own.
        String goals = golem.runningGoalNames();
        if (goals.isEmpty()) {
            goals = "NONE-DEADLOCK";
        }

        PathNavigation nav = golem.getNavigation();
        ItemStack held = golem.getMainHandItem();

        // Live "where would you deposit right now?" - the single most useful
        // fact when a golem freezes holding goods, and safe to ask once.
        BlockPos deliverable = null;
        String deliverErr = "";
        try {
            deliverable = golem.deliverer.getDeliverable();
        } catch (Throwable t) {
            deliverErr = " (getDeliverable threw: " + t.getClass().getSimpleName() + ")";
        }

        Constants.LOG.warn(
            "GOLEM-WATCH STUCK {}s | {} '{}' id={} @{} | goals=[{}] | held={} x{} | nav(done={} target={} pathNull={}) "
          + "| depositTargetKnown={} deliverable={}{} | priorityPos={} home={} | hunger={}/{} | rank={} immortal={}",
            stuckTicks / 20,
            golem.getClass().getSimpleName(),
            golem.displayName(),
            golem.getId(),
            golem.blockPosition(),
            goals,
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()),
            held.getCount(),
            nav.isDone(),
            nav.getTargetPos(),
            nav.getPath() == null,
            golem.hasDepositTarget(),
            deliverable,
            deliverErr,
            golem.getPriorityPos(),
            golem.getHomePos(),
            golem.getHunger(),
            Constants.Golem.maxHunger,
            golem.getRank(),
            golem.isImmortal());
    }

    /**
     * A golem left the world for good. Golems die and vanish completely
     * silently in this mod, which is why "one of my golems is missing" has
     * never once been answerable after the fact - there was simply no record.
     *
     * <p>Only permanent departures are logged. Chunk unloads and dimension
     * changes are normal churn and stay silent, so this costs nothing in a
     * quiet session.
     *
     * @param reason KILLED (damage/aging) or DISCARDED (bunkhouse check-in,
     *               bindle capture, retrain/refresh rebuild) - the distinction
     *               is the whole point: DISCARDED is usually something the
     *               player did, KILLED is the golem actually dying.
     */
    /**
     * What has been lost this session, so it can be summarised on the way in.
     *
     * <p>Every death has been logged faithfully since 2026-08. That did not
     * help: twenty-two golems aged out on 04 Sep, all twenty-two lines were
     * written correctly, and nobody read them for four days. Logging an event
     * and telling the player are different jobs.
     */
    private static final java.util.Map<String, Integer> LOST_THIS_SESSION =
            new java.util.LinkedHashMap<>();

    /** Reason -> count, and cleared once reported. */
    public static synchronized java.util.Map<String, Integer> drainLost() {
        java.util.Map<String, Integer> copy =
                new java.util.LinkedHashMap<>(LOST_THIS_SESSION);
        LOST_THIS_SESSION.clear();
        return copy;
    }

    public static synchronized boolean anyLost() {
        return !LOST_THIS_SESSION.isEmpty();
    }

    static void reportGone(StrawGolem golem, String reason, String cause) {
        if (!"DISCARDED".equals(reason)) {
            synchronized (GolemWatch.class) {
                LOST_THIS_SESSION.merge(
                        reason + ("genericKill".equals(cause) ? " (old age)" : " (" + cause + ")"),
                        1, Integer::sum);
            }
        }
        Constants.LOG.warn(
            "GOLEM-WATCH GONE [{}] | {} '{}' id={} @{} | cause={} | rank={} immortal={} hunger={}/{} health={} | held={} home={}",
            reason,
            golem.getClass().getSimpleName(),
            golem.displayName(),
            golem.getId(),
            golem.blockPosition(),
            cause,
            golem.getRank(),
            golem.isImmortal(),
            golem.getHunger(),
            Constants.Golem.maxHunger,
            String.format("%.1f", golem.getHealth()),
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(golem.getMainHandItem().getItem()),
            golem.getHomePos());
    }

    /**
     * Logged when a previously-stuck golem starts moving again. Tells us whether
     * a freeze self-recovered (and after how long) or only ever ends with a
     * nudge - which is exactly the difference between a lag stall and a real
     * goal deadlock.
     */
    static void reportRecovered(StrawGolem golem, int stuckTicks) {
        Constants.LOG.warn("GOLEM-WATCH RECOVERED after {}s | {} id={} @{}",
            stuckTicks / 20,
            golem.getClass().getSimpleName(),
            golem.getId(),
            golem.blockPosition());
    }
}
