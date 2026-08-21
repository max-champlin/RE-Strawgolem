package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.registry.BlockRegistry;

public class GolemWanderGoal extends WaterAvoidingRandomStrollGoal {
    /** Close enough to the bunkhouse to be tucked in. */
    private static final double CHECK_IN_DIST_SQ = 9.0;
    /** Rescan delay (game ticks) after a scan that found no bunkhouse. */
    private static final int RESCAN_TICKS = 60;
    /** How far an idle, chest/home-bound golem strays from its station. */
    private static final double IDLE_LEASH = 5.0;

    private int wanderLimit;
    private BlockPos startPos;
    private BlockPos homePos;
    private long nextScanTime;

    public GolemWanderGoal(StrawGolem golem) {
        super(golem, StrawGolem.defaultWalkSpeed);
        wanderLimit = Constants.Golem.wanderRange;
    }

    @Override
    public void start() {
        super.start();
        startPos = mob.blockPosition();
    }

    @Override
    public void tick() {
        super.tick();
        tryCheckIn();
    }

    /**
     * A golem within reach of its bunkhouse at night or in rain checks in: the
     * entity is stored inside the block (safe, dry, and not aging) until the
     * bunkhouse releases it at dawn. Called from canUse too, so golems already
     * standing at the door still get tucked in.
     */
    private void tryCheckIn() {
        if (!(mob instanceof StrawGolem golem) || golem.isRemoved() || mob.level().isClientSide) {
            return;
        }
        if (!mob.level().isNight()) {
            return;
        }
        BlockPos home = golem.getHomePos() != null ? golem.getHomePos() : homePos;
        if (home == null || home.distToCenterSqr(mob.getX(), mob.getY(), mob.getZ()) > CHECK_IN_DIST_SQ) {
            return;
        }
        if (mob.level().getBlockEntity(home) instanceof org.hero.strawgolem.block.BunkhouseBlockEntity house) {
            house.tryCheckIn(golem);
        }
    }

    /** A nearby spot with a roof over it, or null if already dry / none found. */
    private Vec3 findCover() {
        if (!mob.level().canSeeSky(mob.blockPosition().above())) {
            return null; // already sheltered
        }
        for (int i = 0; i < 10; i++) {
            Vec3 pos = LandRandomPos.getPos(mob, 8, 3);
            if (pos != null && !mob.level().canSeeSky(BlockPos.containing(pos).above())) {
                return pos;
            }
        }
        return null;
    }

    /**
     * The golem's home: a remembered bunkhouse survives any distance (set on
     * discovery and on every check-in); golems that have never seen one scan
     * nearby every few seconds. A demolished house is forgotten.
     */
    private BlockPos findBunkhouse(StrawGolem golem) {
        boolean bedtime = mob.level().isNight();
        BlockPos known = golem.getHomePos();
        if (known != null && mob.level().isLoaded(known)
                && !mob.level().getBlockState(known).is(BlockRegistry.GOLEM_BUNKHOUSE.get())) {
            golem.setHomePos(null); // house was demolished; forget it
            known = null;
        }
        // By day a known home is simply kept; at bedtime we re-evaluate, so a
        // closer vacant house (or any vacancy when home is full) wins the night.
        boolean knownFull = known != null && bedtime && mob.level().isLoaded(known)
                && mob.level().getBlockEntity(known) instanceof org.hero.strawgolem.block.BunkhouseBlockEntity h
                && !h.hasRoom();
        if (known != null && !bedtime) {
            return known;
        }
        long now = mob.level().getGameTime();
        if (now < nextScanTime) {
            if (known != null && !knownFull) {
                return known;
            }
            return homePos;
        }
        nextScanTime = now + RESCAN_TICKS;
        BlockPos center = mob.blockPosition();
        int r = Math.max(Constants.Golem.searchRange, 24);
        BlockPos bestVacant = null;
        BlockPos bestAny = null;
        double dVacant = Double.MAX_VALUE;
        double dAny = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            if (!mob.level().getBlockState(pos).is(BlockRegistry.GOLEM_BUNKHOUSE.get())) {
                continue;
            }
            double d = pos.distToCenterSqr(mob.getX(), mob.getY(), mob.getZ());
            boolean room = !(mob.level().getBlockEntity(pos) instanceof org.hero.strawgolem.block.BunkhouseBlockEntity be)
                    || be.hasRoom();
            if (room && d < dVacant) {
                dVacant = d;
                bestVacant = pos.immutable();
            }
            if (d < dAny) {
                dAny = d;
                bestAny = pos.immutable();
            }
        }
        // Nearest vacant house wins the night; the old home only keeps its
        // tenant if it is still the closest option with room.
        double knownDist = known != null && !knownFull
                ? known.distToCenterSqr(mob.getX(), mob.getY(), mob.getZ())
                : Double.MAX_VALUE;
        BlockPos chosen;
        if (bestVacant != null && dVacant < knownDist) {
            chosen = bestVacant;
        } else if (known != null && !knownFull) {
            chosen = known;
        } else {
            chosen = bestVacant != null ? bestVacant : bestAny;
        }
        if (chosen != null) {
            golem.setHomePos(chosen); // this is my house now (until somewhere closer has a bed)
            homePos = chosen;
            return chosen;
        }
        return known;
    }

    /**
     * Night or rain sends golems home to the bunkhouse (any distance - they
     * remember the address); a golem with no home holds position instead of
     * drifting off in the dark. By day, golems keep near their bound chest, or
     * failing that near their bunkhouse, so nobody migrates to the village.
     */
    @Override
    protected Vec3 getPosition() {
        // Rain no longer sends anyone home - they only knock off at night.
        boolean raining = false;
        if (mob instanceof StrawGolem golem) {
            if (mob.level().isNight() || raining) {
                BlockPos home = findBunkhouse(golem);
                if (home != null) {
                    Vec3 anchor = Vec3.atCenterOf(home);
                    double distSq = mob.position().distanceToSqr(anchor);
                    if (distSq > 100.0) {
                        Vec3 toward = LandRandomPos.getPosTowards(mob, 16, 7, anchor);
                        return toward != null ? toward : anchor;
                    }
                    if (distSq > 4.0) {
                        return anchor; // walk right up to the door
                    }
                    // At the door: check-in happens via canUse/tick.
                    return raining ? findCover() : null;
                }
                if (raining) {
                    if (!mob.level().canSeeSky(mob.blockPosition().above())) {
                        return null; // already dry: stay put
                    }
                    Vec3 covered = findCover();
                    if (covered != null) {
                        return covered;
                    }
                }
                // No home known: hold position rather than wandering in the dark.
                return null;
            }
            // Daytime tether: bound chest first, home bunkhouse second.
            // Homeless golems adopt a nearby lodge on sight, so new hires
            // don't spend their first day sightseeing in the village.
            BlockPos chest = golem.getPriorityPos();
            BlockPos anchorPos = chest.getX() != Integer.MAX_VALUE ? chest : findBunkhouse(golem);
            if (anchorPos != null) {
                Vec3 anchor = Vec3.atCenterOf(anchorPos);
                // Idle golems mill close to their station, not out at the
                // tether edge - the less they pace the fence line, the less
                // they slip through the diagonal gap at a fence corner.
                double leash = Math.min(Constants.Golem.tetherRange, IDLE_LEASH);
                if (mob.position().distanceToSqr(anchor) > leash * leash) {
                    Vec3 back = LandRandomPos.getPosTowards(mob, (int) Math.ceil(leash), 7, anchor);
                    return back != null ? back : anchor;
                }
                // Wander only to nearby ground biased back toward the anchor.
                return LandRandomPos.getPosTowards(mob, (int) leash, 4, anchor);
            }
        }
        return super.getPosition();
    }

    @Override
    public boolean canUse() {
        // Tuck-in check runs even when no strolling is needed, so a golem
        // already standing at the door still gets a bunk.
        tryCheckIn();
        if (mob.isRemoved()) {
            return false;
        }
        // Carrying goods normally means "go deliver it, don't wander off" - but
        // ONLY if there is somewhere to deliver to. A golem that harvested out
        // of range of every chest can't harvest (hand full) and can't deposit
        // (no target); if it can't wander either it has no runnable goal and
        // stands frozen until GoHomeGoal fires at nightfall. Letting it stroll
        // (tethered back toward its chest/lodge) is what breaks that deadlock.
        if (!this.mob.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                && (!(mob instanceof StrawGolem sg) || sg.hasDepositTarget())) {
            return false;
        }
        // Going home or getting rained on is urgent: skip the stroll interval.
        if (false) {
            this.trigger();
        } else if (mob.level().isNight() && mob instanceof StrawGolem golem
                && golem.getHomePos() != null
                && golem.getHomePos().distToCenterSqr(mob.getX(), mob.getY(), mob.getZ()) > CHECK_IN_DIST_SQ) {
            this.trigger();
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && startPos.distManhattan(mob.blockPosition()) < wanderLimit;
    }
}
