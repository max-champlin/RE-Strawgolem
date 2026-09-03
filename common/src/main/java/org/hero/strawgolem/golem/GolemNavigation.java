package org.hero.strawgolem.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * A navigator that will not run the same hopeless search twice a tick.
 *
 * <p>Nearly every goal in this mod asks for a path with some form of
 * {@code if (getNavigation().isDone()) moveTo(target)}. That is a perfectly good
 * throttle <em>while the path succeeds</em> - {@code isDone()} goes false as the
 * golem walks, so the search happens once. It falls apart the moment a target
 * cannot be pathed to at all: {@code moveTo} fails, {@code isDone()} stays true,
 * and the goal runs a full A* search <b>every tick, forever</b>.
 *
 * <p>That is not hypothetical. On 2026-09-02 one golem sat in that state for
 * <b>12,258 seconds</b> - three and a half hours of A* at twenty searches a
 * second - and the watcher logged seventeen stuck episodes across an evening,
 * seven of them with {@code pathNull=true}. The server ran at ~16 TPS for the
 * whole session.
 *
 * <p>Fixing it in twenty-eight goals would mean twenty-eight chances to get it
 * wrong. Every one of them funnels into
 * {@link #createPath(Set, int, boolean, int, float)}, so the throttle lives here
 * instead, once.
 *
 * <h2>What it does and does not do</h2>
 *
 * <p>It backs off <b>only when the search returns nothing at all</b>. A null path
 * means A* explored and found no route; running it again a tick later, from the
 * same block, to the same target, cannot produce a different answer. Those are
 * the calls that are pure waste and they are the ones skipped.
 *
 * <p>A <em>partial</em> path - one that cannot reach the target but gets closer -
 * is always returned untouched. Vanilla relies on partial paths to approach
 * things, and suppressing them would leave golems standing still in situations
 * where they currently shuffle usefully closer. Being too clever here would trade
 * a performance bug for a behaviour bug.
 *
 * <p>The backoff clears itself the moment anything might have changed: a
 * different target, or the golem having moved {@value #MOVED_ENOUGH} blocks,
 * because a route that does not exist from here may well exist from over there.
 */
public class GolemNavigation extends GroundPathNavigation {

    /** First retry delay after a failed search, in ticks. Doubles from here. */
    private static final int BASE_BACKOFF = 20;

    /** Ceiling on the backoff - ten seconds between hopeless searches. */
    private static final int MAX_BACKOFF = 200;

    /** Move this far and the world has changed enough to be worth asking again. */
    private static final double MOVED_ENOUGH = 3.0;

    /** Cap on doubling, so the shift cannot run away. */
    private static final int MAX_FAILURES = 8;

    private Set<BlockPos> lastFailed;
    private Vec3 failedFrom;
    private int failures;
    private long retryAfter;

    public GolemNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward,
                              int accuracy, float followRange) {
        if (suppressed(targets)) {
            return null;
        }
        Path path = super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        if (path == null) {
            recordFailure(targets);
        } else {
            failures = 0;
            lastFailed = null;
        }
        return path;
    }

    /** True when this exact search just failed and nothing has changed since. */
    private boolean suppressed(Set<BlockPos> targets) {
        if (failures == 0 || lastFailed == null || targets == null) {
            return false;
        }
        if (level.getGameTime() >= retryAfter) {
            return false;
        }
        if (!lastFailed.equals(targets)) {
            return false;
        }
        // Moved somewhere new? Then the answer might genuinely be different now.
        return failedFrom != null
                && mob.position().distanceToSqr(failedFrom) < MOVED_ENOUGH * MOVED_ENOUGH;
    }

    private void recordFailure(Set<BlockPos> targets) {
        if (lastFailed == null || !lastFailed.equals(targets)) {
            failures = 0;
            // Immutable copies on purpose. Callers legitimately hand in a
            // reused MutableBlockPos - VisionHelper sweeps 26,000 positions
            // with one cursor - and keeping a reference to that would mean this
            // set silently changed contents between ticks.
            lastFailed = new HashSet<>();
            for (BlockPos target : targets) {
                lastFailed.add(target.immutable());
            }
        }
        failures = Math.min(failures + 1, MAX_FAILURES);
        failedFrom = mob.position();
        retryAfter = level.getGameTime()
                + Math.min(BASE_BACKOFF << (failures - 1), MAX_BACKOFF);
    }
}
