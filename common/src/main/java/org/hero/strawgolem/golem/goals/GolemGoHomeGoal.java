package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.block.BunkhouseBlockEntity;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.registry.BlockRegistry;

import java.util.EnumSet;

/**
 * At night a golem drops its work and heads home to its bunkhouse to check in.
 * High priority, so even a golem with endless work (a full crop field) still
 * sleeps - which is what lets it age-freeze and be auto-retired. Without this,
 * busy golems never idle, never sleep, and quietly die of old age at their post.
 */
public class GolemGoHomeGoal extends Goal {
    private static final int RESCAN_TICKS = 40;
    private static final double CHECK_IN_DIST_SQ = 9.0;

    private final StrawGolem golem;
    private BlockPos home;
    private long nextScanTime;

    public GolemGoHomeGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean bedtime() {
        return golem.level().isNight();
    }

    private boolean isBunkhouse(BlockPos pos) {
        return golem.level().getBlockState(pos).is(BlockRegistry.GOLEM_BUNKHOUSE.get());
    }

    /** Remembered home if it still has room, else the nearest vacant bunkhouse. */
    private BlockPos findBunkhouse() {
        BlockPos known = golem.getHomePos();
        if (known != null && golem.level().isLoaded(known)) {
            if (!isBunkhouse(known)) {
                golem.setHomePos(null);
            } else if (golem.level().getBlockEntity(known) instanceof BunkhouseBlockEntity h && h.hasRoom()) {
                return known;
            }
        }
        long now = golem.level().getGameTime();
        if (now < nextScanTime) {
            return known != null && isBunkhouse(known) ? known : null;
        }
        nextScanTime = now + RESCAN_TICKS;
        BlockPos center = golem.blockPosition();
        int r = Math.max(Constants.Golem.searchRange, 24);
        BlockPos bestVacant = null;
        BlockPos bestAny = null;
        double dVacant = Double.MAX_VALUE;
        double dAny = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            if (!isBunkhouse(pos)) {
                continue;
            }
            double d = pos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ());
            boolean room = !(golem.level().getBlockEntity(pos) instanceof BunkhouseBlockEntity be) || be.hasRoom();
            if (room && d < dVacant) {
                dVacant = d;
                bestVacant = pos.immutable();
            }
            if (d < dAny) {
                dAny = d;
                bestAny = pos.immutable();
            }
        }
        BlockPos chosen = bestVacant != null ? bestVacant : bestAny;
        if (chosen != null) {
            golem.setHomePos(chosen);
        }
        return chosen;
    }

    @Override
    public boolean canUse() {
        if (!bedtime()) {
            return false;
        }
        home = findBunkhouse();
        return home != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bedtime() && home != null && !golem.isRemoved() && isBunkhouse(home);
    }

    @Override
    public void start() {
        moveToHome();
    }

    @Override
    public void stop() {
        home = null;
        golem.getNavigation().stop();
    }

    private void moveToHome() {
        golem.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, Constants.Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (home == null) {
            return;
        }
        golem.getLookControl().setLookAt(home.getX() + 0.5, home.getY() + 0.5, home.getZ() + 0.5);
        if (home.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > CHECK_IN_DIST_SQ) {
            if (golem.getNavigation().isDone()) {
                moveToHome();
            }
            return;
        }
        if (golem.level().getBlockEntity(home) instanceof BunkhouseBlockEntity house && !house.tryCheckIn(golem)) {
            // Full - forget this one and look for another next scan.
            golem.setHomePos(null);
            home = null;
        }
    }
}
