package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * A shared "dibs" board for harvest targets: when a golem commits to a crop it
 * claims that position, and other golems skip claimed crops. Claims expire on
 * their own, so a golem that dies or unloads never leaves a crop locked. Keeps
 * a crowded field from having ten golems race for the same plant.
 */
public final class CropClaims {
    private static final long CLAIM_DURATION = 200; // ticks a claim stays live

    private record Claim(int golemId, long expiry) {}

    private static final Map<BlockPos, Claim> CLAIMS = new HashMap<>();

    private CropClaims() {}

    /** Whether another golem currently holds this crop. */
    public static boolean isTakenByOther(BlockPos pos, int golemId, long now) {
        Claim c = CLAIMS.get(pos);
        if (c == null) {
            return false;
        }
        if (c.expiry() <= now) {
            CLAIMS.remove(pos);
            return false;
        }
        return c.golemId() != golemId;
    }

    /** Stakes (or refreshes) this golem's claim on a crop. */
    public static void claim(BlockPos pos, int golemId, long now) {
        CLAIMS.put(pos.immutable(), new Claim(golemId, now + CLAIM_DURATION));
    }

    /** Releases a claim if this golem owns it. */
    public static void release(BlockPos pos, int golemId) {
        if (pos == null) {
            return;
        }
        Claim c = CLAIMS.get(pos);
        if (c != null && c.golemId() == golemId) {
            CLAIMS.remove(pos);
        }
    }
}
