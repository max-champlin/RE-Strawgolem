package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A shared "don't bother with that one" board for deposit chests.
 *
 * <p>Two different things stop a deposit, and they need very different memories:
 *
 * <ul>
 *   <li><b>Full</b> - the chest would take this item, but has no room right now.
 *       That changes the moment you empty it, so the flag is short (~10s) and
 *       golems drift back on their own.</li>
 *   <li><b>Rejected</b> - the container refuses this item on principle: a
 *       food-only Lunch Cart offered essence, a filtered storage chest, a
 *       type-locked drawer. That answer will <i>never</i> change, so a 10s flag
 *       just means the golem walks back every 10 seconds forever - offer,
 *       refuse, turn around, repeat. Rejections are remembered per
 *       (container, item) for much longer.</li>
 * </ul>
 *
 * <p>Treating a permanent refusal as temporary fullness is what had golems
 * pacing between a rejecting container and the field indefinitely.
 */
public final class FullChests {
    /** Ticks a "full right now" flag lingers (~10s) - clears itself once you unload the chest. */
    private static final long FULL_DURATION = 200;
    /** Ticks a "won't ever take this item" flag lingers (~5min) - long enough to stop the pacing. */
    private static final long REJECT_DURATION = 6000;
    /** Prune no more often than this, so a long session never walks a big map every tick. */
    private static final long PRUNE_INTERVAL = 1200;

    private static final Map<BlockPos, Long> FULL = new HashMap<>();
    private static final Map<Reject, Long> REJECTED = new HashMap<>();
    private static long nextPrune = 0L;

    private FullChests() {}

    /** Identity of "this container refuses this kind of item". */
    private static final class Reject {
        private final BlockPos pos;
        private final Item item;

        Reject(BlockPos pos, Item item) {
            this.pos = pos.immutable();
            this.item = item;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Reject other)) return false;
            return pos.equals(other.pos) && item == other.item;
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, item);
        }
    }

    /** Whether this chest was recently found full (and the flag hasn't expired). */
    public static boolean isFull(BlockPos pos, long now) {
        Long expiry = FULL.get(pos);
        if (expiry == null) {
            return false;
        }
        if (expiry <= now) {
            FULL.remove(pos);
            return false;
        }
        return true;
    }

    /**
     * Whether this container is off-limits for this particular stack - either
     * full right now, or known to refuse this item outright. Use this wherever a
     * held stack is available; it is strictly better than {@link #isFull}.
     */
    public static boolean isBlocked(BlockPos pos, ItemStack stack, long now) {
        if (isFull(pos, now)) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Reject key = new Reject(pos, stack.getItem());
        Long expiry = REJECTED.get(key);
        if (expiry == null) {
            return false;
        }
        if (expiry <= now) {
            REJECTED.remove(key);
            return false;
        }
        return true;
    }

    /** Flags a chest as full so golems route around it for a while. */
    public static void markFull(BlockPos pos, long now) {
        FULL.put(pos.immutable(), now + FULL_DURATION);
    }

    /**
     * Flags a container as refusing this item, so golems stop re-offering it.
     * Only the offered item type is blocked - the same chest stays available for
     * everything else the crew hauls.
     */
    public static void markRejected(BlockPos pos, ItemStack stack, long now) {
        if (stack == null || stack.isEmpty()) {
            markFull(pos, now);
            return;
        }
        REJECTED.put(new Reject(pos, stack.getItem()), now + REJECT_DURATION);
        prune(now);
    }

    /** Clears both flags early (e.g. a successful deposit proves there's room). */
    public static void clear(BlockPos pos) {
        if (pos == null) {
            return;
        }
        FULL.remove(pos);
        REJECTED.keySet().removeIf(key -> key.pos.equals(pos));
    }

    /** Drops expired entries so a long session can't grow the maps forever. */
    private static void prune(long now) {
        if (now < nextPrune) {
            return;
        }
        nextPrune = now + PRUNE_INTERVAL;
        REJECTED.values().removeIf(expiry -> expiry <= now);
        FULL.values().removeIf(expiry -> expiry <= now);
    }
}
