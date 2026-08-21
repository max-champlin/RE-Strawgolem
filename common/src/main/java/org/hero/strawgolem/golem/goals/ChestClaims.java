package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * A shared board of who is currently walking to which deposit chest. Golems all
 * compute the same "nearest chest" answer, so without this they pile onto one
 * container and shove each other off it while two perfectly good chests sit
 * empty. Claims expire on their own, so a golem that dies, unloads or changes
 * its mind never leaves a chest looking busy.
 */
public final class ChestClaims {
    /** Ticks a claim stays live if it isn't refreshed. */
    private static final long CLAIM_DURATION = 100;

    private static final Map<BlockPos, Map<Integer, Long>> CLAIMS = new HashMap<>();

    private ChestClaims() {}

    /** Stakes (or refreshes) this golem's claim on a chest. */
    public static void claim(BlockPos pos, int golemId, long now) {
        if (pos == null) {
            return;
        }
        CLAIMS.computeIfAbsent(pos.immutable(), p -> new HashMap<>()).put(golemId, now + CLAIM_DURATION);
    }

    /** Drops this golem's claim (deposited, gave up, or switched chests). */
    public static void release(BlockPos pos, int golemId) {
        if (pos == null) {
            return;
        }
        Map<Integer, Long> holders = CLAIMS.get(pos);
        if (holders != null) {
            holders.remove(golemId);
            if (holders.isEmpty()) {
                CLAIMS.remove(pos);
            }
        }
    }

    /** How many OTHER golems are currently headed to this chest. */
    public static int others(BlockPos pos, int golemId, long now) {
        if (pos == null) {
            return 0;
        }
        Map<Integer, Long> holders = CLAIMS.get(pos);
        if (holders == null) {
            return 0;
        }
        holders.values().removeIf(expiry -> expiry <= now);
        if (holders.isEmpty()) {
            CLAIMS.remove(pos);
            return 0;
        }
        int count = 0;
        for (Integer id : holders.keySet()) {
            if (id != golemId) {
                count++;
            }
        }
        return count;
    }
}
