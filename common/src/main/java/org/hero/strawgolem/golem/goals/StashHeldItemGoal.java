package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Walk whatever is in hand to the bound chest and put it away.
 *
 * <p>Six professions wanted exactly this and each had its own copy - Cook's and
 * Smelter's were identical once you renamed the type. They differed only in how
 * long to wait before giving up on the item ({@code idleBeforeStash}) and in an
 * optional "actually, still working" test that defers the trip. Both are
 * constructor arguments now; the subclasses below are name-only, so the goal a
 * profession registers still reads as its own.
 *
 * <p>The subclasses are kept rather than folded away so that a stack trace, a
 * {@code GOLEM-WATCH} report or the Employee Directory still names the
 * profession rather than saying "StashHeldItemGoal" fourteen times.
 */
public class StashHeldItemGoal extends Goal {

    /**
     * How often to ask the navigator for a fresh path while walking to the chest.
     *
     * <p>Every copy of this goal previously called {@code moveTo} on EVERY tick,
     * which throws away the current path and runs a full A* search twenty times a
     * second, per golem. Vanilla's own {@link net.minecraft.world.entity.ai.goal.MoveToBlockGoal}
     * repaths every 40 ticks for the same reason; these goals extend plain
     * {@link Goal} and so inherited no such throttle. Twenty ticks is half
     * vanilla's interval - a stationary chest does not move, and the only thing a
     * fresh path buys is recovering from having been shoved off course.
     */
    private static final int REPATH_INTERVAL = 20;

    /**
     * Ticks of walking without getting any closer before the trip is abandoned.
     *
     * <p>A chest can be pathable and still unreachable - behind a fence, up a
     * block, across a gap the golem will not jump. The navigator hands back a
     * partial path, the golem walks to the end of it, {@code isDone()} goes true,
     * and the goal paths again to the same place forever. The watcher logged
     * eight of these on {@code CookStashGoal} in one evening, one lasting 595
     * seconds, all reporting {@code nav(done=true pathNull=false)}.
     *
     * <p>Distance is the honest test. A golem making progress is a golem worth
     * waiting for; one that has not improved in ten seconds is stuck. Ten rather than
     * five because walking around an obstacle legitimately increases the
     * distance for a while, and punishing that would strand golems that are
     * doing exactly the right thing.
     */
    private static final int STALL_LIMIT = 200;

    /** How long to leave it alone after giving up, so it does not retry instantly. */
    private static final int GIVE_UP_COOLDOWN = 200;

    /** Distance improvement that counts as progress rather than jitter. */
    private static final double PROGRESS_EPSILON = 0.25;

    /**
     * Minimum ticks between path searches, even when the navigator says it is
     * finished.
     *
     * <p>The {@code isDone()} escape below exists so a golem that completed a
     * stale path does not stand there forever. But a <em>partial</em> path - one
     * that ends short of an unreachable chest - completes instantly and leaves
     * {@code isDone()} true every tick after, which turns the escape into a full
     * A* search twenty times a second. Five ticks is still responsive and costs
     * a quarter as much.
     */
    private static final int MIN_REPATH_GAP = 5;

    private final StrawGolem golem;
    private final int idleBeforeStash;

    /**
     * Returns true while the golem still has real work to do, which resets the
     * idle timer. Null for professions whose hand is pure transport.
     */
    private final Predicate<StrawGolem> stillBusy;

    private int idleTicks;
    private int repathTicks;
    private int stallTicks;
    private int cooldown;
    private double bestDistSqr;

    public StashHeldItemGoal(StrawGolem golem, int idleBeforeStash, Predicate<StrawGolem> stillBusy) {
        this.golem = golem;
        this.idleBeforeStash = idleBeforeStash;
        this.stillBusy = stillBusy;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (golem.getMainHandItem().isEmpty() || !golem.hasDepositChest()
                || !ContainerHelper.isContainer(golem, golem.getPriorityPos())) {
            idleTicks = 0;
            return false;
        }
        if (stillBusy != null && stillBusy.test(golem)) {
            idleTicks = 0;
            return false;
        }
        return ++idleTicks >= idleBeforeStash;
    }

    @Override
    public boolean canContinueToUse() {
        return stallTicks < STALL_LIMIT
                && !golem.getMainHandItem().isEmpty()
                && ContainerHelper.isContainer(golem, golem.getPriorityPos());
    }

    @Override
    public void start() {
        // Zero rather than REPATH_INTERVAL so the first tick paths immediately
        // instead of standing still for a second.
        repathTicks = 0;
        stallTicks = 0;
        bestDistSqr = Double.MAX_VALUE;
    }

    @Override
    public void stop() {
        idleTicks = 0;
        // Gave up rather than finished: stand down for a while instead of
        // starting the same doomed walk again on the very next tick.
        if (stallTicks >= STALL_LIMIT) {
            cooldown = GIVE_UP_COOLDOWN;
        }
        stallTicks = 0;
        golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos chest = golem.getPriorityPos();
        golem.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
        if (!ReachHelper.canReach(golem, chest)) {
            double distSqr = chest.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ());
            if (distSqr < bestDistSqr - PROGRESS_EPSILON) {
                bestDistSqr = distSqr;
                stallTicks = 0;
            } else {
                stallTicks++;
            }
            // Repath on a timer, or sooner if the navigator has run out of path -
            // otherwise a golem that finished a stale path just stands there.
            // The early route is floored at MIN_REPATH_GAP so a permanently
            // "done" partial path cannot drive a search every tick.
            boolean due = --repathTicks <= 0;
            boolean pathSpent = golem.getNavigation().isDone()
                    && repathTicks <= REPATH_INTERVAL - MIN_REPATH_GAP;
            if (due || pathSpent) {
                repathTicks = REPATH_INTERVAL;
                golem.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5,
                        Golem.defaultWalkSpeed);
            }
            return;
        }
        ItemStack held = golem.getMainHandItem();
        golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        golem.depositToChest(held);
    }
}
