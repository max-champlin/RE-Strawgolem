package org.hero.strawgolem.golem.goals;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ReachHelper;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Lets the breeder golem tidy its pen: any dropped item in range (chicken eggs,
 * modded animal by-products, whatever) gets picked up. Food it can use stays in
 * hand for breeding; everything else is returned to the chest by the stash goal.
 */
public class BreederPickupGoal extends Goal {
    /** Ticks of failing to reach a target before we give up on it. */
    private static final int GIVE_UP_TICKS = 80;
    /** How long (ticks) a given-up item is ignored before we try it again. */
    private static final int IGNORE_TICKS = 200;

    private final StrawGolem golem;
    private final java.util.Map<Integer, Long> ignoreUntil = new java.util.HashMap<>();
    private ItemEntity target;
    private int pickupTicks;
    private int stuckTicks;
    private boolean acquired;

    public BreederPickupGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private ItemEntity findItem() {
        long now = golem.level().getGameTime();
        ignoreUntil.values().removeIf(until -> until <= now);
        List<ItemEntity> items = golem.level().getEntitiesOfClass(ItemEntity.class,
                golem.getBoundingBox().inflate(Golem.searchRange, Golem.searchRangeVertical, Golem.searchRange),
                e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty()
                        && !ignoreUntil.containsKey(e.getId())
                        && golem.mayWorkAt(e.blockPosition())
                        && ReachHelper.canPath(golem, e.blockPosition()));
        return items.stream().min(Comparator.comparingDouble(golem::distanceToSqr)).orElse(null);
    }

    @Override
    public boolean canUse() {
        if (!golem.getMainHandItem().isEmpty() || golem.carryStatus() != 0) {
            return false;
        }
        target = findItem();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && !acquired && golem.getMainHandItem().isEmpty();
    }

    @Override
    public void start() {
        pickupTicks = 0;
        stuckTicks = 0;
        acquired = false;
        moveToTarget();
    }

    /** Abandon the current target and ignore it for a while, so we don't re-grab it. */
    private void giveUp() {
        if (target != null) {
            ignoreUntil.put(target.getId(), golem.level().getGameTime() + IGNORE_TICKS);
        }
        target = null;
    }

    @Override
    public void stop() {
        target = null;
        pickupTicks = 0;
        acquired = false;
        golem.setPickupStatus(0);
        golem.getNavigation().stop();
    }

    private void moveToTarget() {
        golem.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            return;
        }
        golem.getLookControl().setLookAt(target);
        if (!ReachHelper.canReach(golem, target.blockPosition())) {
            // Not there yet: keep pathing, but bail if we can never close the gap.
            if (++stuckTicks > GIVE_UP_TICKS) {
                giveUp();
                golem.setPickupStatus(0);
                golem.getNavigation().stop();
                return;
            }
            if (golem.getNavigation().isDone()) {
                moveToTarget();
            }
            pickupTicks = 0;
            golem.setPickupStatus(0);
            return;
        }
        stuckTicks = 0;
        // In reach: play the little pickup animation, then take the stack.
        if (pickupTicks == 0) {
            target.setPickUpDelay(40);
            golem.setPickupStatus(target.getItem());
            golem.stopInPlace();
        }
        pickupTicks++;
        if (pickupTicks >= 20) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, target.getItem().copy());
            target.setItem(net.minecraft.world.item.ItemStack.EMPTY);
            golem.setPickupStatus(0);
            acquired = true;
        }
    }
}
