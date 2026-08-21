package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.BrewerGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;

import java.util.EnumSet;

/**
 * Collects finished potions: a bottle counts as done when the stand is idle and
 * nothing in the chest can brew it any further. Water bottles are never
 * collected, so unbrewed bases stay put. One potion per trip - they don't
 * stack, and the golem is small.
 */
public class BrewerCollectGoal extends Goal {
    private static final int SCAN_COOLDOWN = 40;

    private final BrewerGolem golem;
    private BlockPos standPos;
    private int slot;
    private boolean done;
    private int scanCooldown;

    public BrewerCollectGoal(BrewerGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Finds a stand with a finished bottle; remembers which slot it is in. */
    private boolean findFinished() {
        for (BlockPos pos : golem.findStands()) {
            if (!(golem.level().getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)
                    || !stand.getItem(3).isEmpty()) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                ItemStack bottle = stand.getItem(i);
                if (!bottle.isEmpty() && !BrewerGolem.isWaterBottle(bottle)
                        && !golem.chestCanBrew(bottle)) {
                    standPos = pos;
                    slot = i;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canUse() {
        if (!golem.getMainHandItem().isEmpty() || !golem.hasDepositChest()
                || !ContainerHelper.isContainer(golem, golem.getPriorityPos())) {
            return false;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_COOLDOWN;
        return findFinished();
    }

    @Override
    public boolean canContinueToUse() {
        return !done && standPos != null && golem.getMainHandItem().isEmpty()
                && golem.level().getBlockEntity(standPos) instanceof BrewingStandBlockEntity stand
                && !stand.getItem(slot).isEmpty();
    }

    @Override
    public void start() {
        done = false;
        moveToStand();
    }

    @Override
    public void stop() {
        standPos = null;
        done = false;
        golem.getNavigation().stop();
    }

    private void moveToStand() {
        golem.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (standPos == null) {
            return;
        }
        golem.getLookControl().setLookAt(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5);
        if (!ReachHelper.canReach(golem, standPos)
                && standPos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > 9.0) {
            if (golem.getNavigation().isDone()) {
                moveToStand();
            }
            return;
        }
        if (golem.level().getBlockEntity(standPos) instanceof BrewingStandBlockEntity stand) {
            ItemStack bottle = stand.getItem(slot);
            if (!bottle.isEmpty() && !BrewerGolem.isWaterBottle(bottle) && !golem.chestCanBrew(bottle)) {
                golem.setItemSlot(EquipmentSlot.MAINHAND, stand.removeItem(slot, bottle.getCount()));
            }
        }
        done = true;
    }
}
