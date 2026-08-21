package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.block.LunchCartBlockEntity;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.registry.BlockRegistry;

import java.util.EnumSet;

/**
 * When hunger is enabled and a golem gets peckish, it walks to the nearest
 * Lunch Cart and eats. Food comes only from the cart - never from a golem's
 * work chest - so Cooks and Janitors don't eat their own inventory.
 */
public class GolemEatGoal extends Goal {
    private static final int EAT_TICKS = 20;
    private static final int FIND_RANGE = 24;
    private static final int FIND_RANGE_V = 6;
    private static final int RESCAN_TICKS = 40;
    /** Ticks of no progress toward the cart before we give up on it (~10s). */
    private static final int APPROACH_GIVE_UP = 200;
    /** Re-issue the path this often while stalled, in case navigation quietly died. */
    private static final int REPATH_EVERY = 40;
    /** How long an unreachable cart stays skipped for THIS golem (~30s). */
    private static final long IGNORE_DURATION = 600;
    /**
     * Reach for EATING only - deliberately not the shared depositDistance (1.5).
     *
     * <p>Measured in the field: a golem sent to a cart parks about 1.45 blocks
     * from the block's centre, because navigation can't stand ON the cart and
     * stops on the neighbouring tile with some slack. Add the 0.5 from the
     * block's centre being half a block down and the real distance lands at
     * 1.50-1.56 - just past a "must be under 1.5" test. The golem stood right
     * at lunch, reported navDone, and starved by six hundredths of a block.
     *
     * <p>2.5 covers any adjacent tile including diagonals and a cart sunk a
     * block into the floor. Kept local so it can't disturb deposit behaviour,
     * where walking right up to the chest is the wanted look.
     */
    private static final double EAT_REACH = 2.5;

    private final StrawGolem golem;
    private BlockPos cartPos;
    private int eatTicks;
    private long nextScanTime;
    /**
     * Approach watchdog state. Without this, an unreachable cart wedges the
     * golem permanently: tick() re-pathed forever, and canContinueToUse only
     * releases when hunger drops - which cannot happen while it isn't eating.
     * Since this goal sits at priority 0 it also outranks harvest, deposit and
     * wander, so a golem that can't reach lunch stops doing anything at all
     * until nightfall (GoHomeGoal is the only thing that can preempt it).
     */
    private int approachTicks;
    private double lastDist = Double.MAX_VALUE;
    /** Carts this golem currently can't path to, with expiry times. */
    private final java.util.Map<BlockPos, Long> ignoreUntil = new java.util.HashMap<>();

    public GolemEatGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LunchCartBlockEntity cartAt(BlockPos pos) {
        return pos != null && golem.level().getBlockEntity(pos) instanceof LunchCartBlockEntity cart ? cart : null;
    }

    /** Nearest stocked Lunch Cart, rescanned on a short cooldown. */
    private BlockPos findCart() {
        long now = golem.level().getGameTime();
        if (now < nextScanTime) {
            return cartAt(cartPos) != null ? cartPos : null;
        }
        nextScanTime = now + RESCAN_TICKS;
        ignoreUntil.values().removeIf(expiry -> expiry <= now);
        BlockPos center = golem.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-FIND_RANGE, -FIND_RANGE_V, -FIND_RANGE),
                center.offset(FIND_RANGE, FIND_RANGE_V, FIND_RANGE))) {
            if (!golem.level().getBlockState(pos).is(BlockRegistry.LUNCH_CART.get())) {
                continue;
            }
            // Skip a cart we already proved we can't get to, so we pick another
            // (or none, and go back to work) instead of re-walking at it.
            Long skip = ignoreUntil.get(pos);
            if (skip != null && skip > now) {
                continue;
            }
            if (golem.level().getBlockEntity(pos) instanceof LunchCartBlockEntity cart && cart.servings() > 0) {
                double d = pos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ());
                if (d < bestDist) {
                    bestDist = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        // NOTE: this used to also require an EMPTY hand, which starved the crew.
        // A working golem is nearly always carrying something between harvest
        // and chest, so the only chance to eat was the split second after a
        // deposit - and if delivery was ever slow, it never ate at all, got
        // slower from hunger, took even longer to deliver, and starved harder.
        // A golem may now put its lunch ahead of its cargo.
        if (!Golem.hunger || golem.getHunger() <= Golem.maxHunger / 2) {
            return false;
        }
        cartPos = findCart();
        return cartPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return cartPos != null
                && golem.getHunger() > Golem.maxHunger / 3
                && cartAt(cartPos) != null;
    }

    @Override
    public void start() {
        eatTicks = 0;
        approachTicks = 0;
        lastDist = Double.MAX_VALUE;
        moveToCart();
    }

    @Override
    public void stop() {
        cartPos = null;
        eatTicks = 0;
        approachTicks = 0;
        lastDist = Double.MAX_VALUE;
        golem.getNavigation().stop();
    }

    /** Close enough to eat. See {@link #EAT_REACH} for why this isn't depositDistance. */
    private boolean canReachCart() {
        return cartPos != null && cartPos.closerToCenterThan(golem.position(), EAT_REACH);
    }

    private void moveToCart() {
        golem.getNavigation().moveTo(cartPos.getX() + 0.5, cartPos.getY(), cartPos.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (cartPos == null) {
            return;
        }
        golem.getLookControl().setLookAt(cartPos.getX() + 0.5, cartPos.getY() + 0.5, cartPos.getZ() + 0.5);
        if (!canReachCart()) {
            // APPROACH WATCHDOG. Progress is measured by distance closing, not a
            // flat timer, so a long walk across the field is never abandoned -
            // only a golem that genuinely isn't getting there gives up.
            double dist = Math.sqrt(cartPos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()));
            if (dist < lastDist - 0.05) {
                approachTicks = 0;
                lastDist = dist;
            } else {
                approachTicks++;
            }
            if (golem.getNavigation().isDone() || approachTicks % REPATH_EVERY == 0) {
                moveToCart();
            }
            // Only bail once navigation itself says it has ARRIVED and we are
            // still short. A hungry golem walks at 25% speed (the starvation
            // floor), so judging it on progress alone cancelled the lunch of
            // golems that were simply crawling - which kept them hungry, which
            // kept them slow. navDone is the honest "can't get there" signal.
            if (approachTicks > APPROACH_GIVE_UP && golem.getNavigation().isDone()) {
                // Can't get to this cart. Skip it for a while and release the
                // goal so harvest/deposit/wander can run again. The golem stays
                // hungry and will retry once the flag lapses - hungry and
                // working beats a statue standing next to lunch it can't touch.
                //
                // Log the ACTUAL cart block and the real distance. Reading the
                // cart's position out of the navigation target is guesswork -
                // that's a path node, not the cart - so record the thing itself.
                org.hero.strawgolem.Constants.LOG.warn(
                    "GOLEM-WATCH EAT-GIVEUP id={} golemPos={} cart={} dist={} reachNeeds<{} | canPath={} navDone={} servings={}",
                    golem.getId(),
                    String.format("%.2f,%.2f,%.2f", golem.getX(), golem.getY(), golem.getZ()),
                    cartPos,
                    String.format("%.3f", dist),
                    org.hero.strawgolem.Constants.Golem.depositDistance,
                    ReachHelper.canPath(golem, cartPos),
                    golem.getNavigation().isDone(),
                    cartAt(cartPos) == null ? -1 : cartAt(cartPos).servings());
                ignoreUntil.put(cartPos.immutable(), golem.level().getGameTime() + IGNORE_DURATION);
                cartPos = null;
                golem.getNavigation().stop();
            }
            eatTicks = 0;
            return;
        }
        eatTicks++;
        if (eatTicks < EAT_TICKS) {
            return;
        }
        LunchCartBlockEntity cart = cartAt(cartPos);
        if (cart == null) {
            return;
        }
        ItemStack food = cart.takeOneFood();
        if (!food.isEmpty()) {
            golem.nourish();
            golem.playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
            if (golem.level() instanceof ServerLevel server) {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food),
                        golem.getX(), golem.getY() + 0.7, golem.getZ(), 8, 0.15, 0.1, 0.15, 0.02);
            }
        }
        eatTicks = 0;
    }
}
