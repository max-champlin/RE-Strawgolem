package org.hero.strawgolem.golem.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class VisionHelper {
    public static boolean canSee(Entity pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) {
            // Yes this is overengineered...
            String error = "";
            if (pos1 == null) error += "Entity";
            if (pos2 == null) error += "BlockPos";
            Constants.LOG.error("VisonHelper error! " + error);
            return false;
        }
        if (Math.abs(pos1.getY() - pos2.getY()) > Golem.searchRangeVertical) return false;
        return Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) + Math.pow(pos1.getZ() - pos2.getZ(), 2)) <= Golem.searchRange;
    }

    /**
     * The nearest position passing the test, or null.
     *
     * <p>Same cube and the same mutable-cursor reasoning as
     * {@link #nearbyBlocks}: 26,011 positions at the configured range, and
     * previously 26,011 allocations to walk them.
     */
    public static BlockPos findNearestBlock(StrawGolem golem, BiPredicate test) {
        int range = Constants.Golem.searchRange;
        BlockPos closest = null;
        int closestDist = Integer.MAX_VALUE;
        BlockPos query = golem.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -range; x <= range; ++x) {
            for (int y = -range / 2; y <= range / 2; ++y) {
                for (int z = -range; z <= range; ++z) {
                    cursor.set(query.getX() + x, query.getY() + y, query.getZ() + z);
                    if (test.filter(golem, cursor)) {
                        int dist = query.distManhattan(cursor);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = cursor.immutable();
                        }
                    }
                }
            }
        }
        return closest;
    }

    /**
     * Every position in the search cube that passes the test, nearest first.
     *
     * <p>The cube is {@code (2r+1)² × (r+1)} blocks - at the configured harvest
     * range of 18 that is <b>26,011 positions per call</b>. It used to allocate a
     * fresh {@link BlockPos} for each of them via {@code offset()}, so one sweep
     * produced 26,000 short-lived objects, and a crew of twenty-five golems
     * sweeping once a second produced most of a garbage collection on its own. A
     * single mutable cursor does the same work with one allocation.
     *
     * <p>Only positions that actually pass the test are made immutable and kept.
     * Anything the predicate passes onward must copy it - {@code GolemNavigation}
     * does exactly that, for exactly this reason.
     */
    public static Queue<BlockPos> nearbyBlocks(StrawGolem golem, BiPredicate test) {
        int range = Constants.Golem.searchRange;
        BlockPos query = golem.blockPosition();
        // Hoisted: this comparator runs O(n log n) times and blockPosition()
        // builds a new BlockPos on every single call.
        Queue<BlockPos> queue = new PriorityQueue<>(
                Comparator.comparingInt(pos -> pos.distManhattan(query)));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -range; x <= range; ++x) {
            for (int y = -range / 2; y <= range / 2; ++y) {
                for (int z = -range; z <= range; ++z) {
                    cursor.set(query.getX() + x, query.getY() + y, query.getZ() + z);
                    if (test.filter(golem, cursor)) {
                        queue.add(cursor.immutable());
                    }
                }
            }
        }
        return queue;
    }
}
