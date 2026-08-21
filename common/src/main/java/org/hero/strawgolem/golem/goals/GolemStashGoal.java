package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.golem.api.VisionHelper;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Generic "put whatever I'm holding back in the chest" goal. A profession
 * supplies a predicate for items that are still useful in hand (a bucket while
 * there are cows to milk, bonemeal while crops need it); anything else is
 * returned to the bound chest after a short idle delay.
 */
public class GolemStashGoal extends Goal {
    private final StrawGolem golem;
    private final Predicate<ItemStack> stillUseful;
    private final int idleDelay;
    private BlockPos chestPos;
    private int idleTicks;

    public GolemStashGoal(StrawGolem golem, Predicate<ItemStack> stillUseful, int idleDelay) {
        this.golem = golem;
        this.stillUseful = stillUseful;
        this.idleDelay = idleDelay;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private BlockPos findStashContainer() {
        BlockPos prio = golem.getPriorityPos();
        if (prio.getX() != Integer.MAX_VALUE && ContainerHelper.isContainer(golem, prio)
                && ReachHelper.canPath(golem, prio)) {
            return prio;
        }
        org.hero.strawgolem.golem.api.BiPredicate<BlockPos> pred = (gol, pos) ->
                ContainerHelper.isContainer(gol, pos) && ReachHelper.canPath(gol, pos);
        return VisionHelper.findNearestBlock(golem, pred);
    }

    @Override
    public boolean canUse() {
        if (golem.getMainHandItem().isEmpty() || stillUseful.test(golem.getMainHandItem())) {
            idleTicks = 0;
            return false;
        }
        if (++idleTicks < idleDelay) {
            return false;
        }
        chestPos = findStashContainer();
        return chestPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return chestPos != null && !golem.getMainHandItem().isEmpty()
                && ContainerHelper.isContainer(golem, chestPos);
    }

    @Override
    public void start() {
        moveToChest();
    }

    @Override
    public void stop() {
        chestPos = null;
        idleTicks = 0;
        golem.getNavigation().stop();
    }

    private void moveToChest() {
        golem.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        golem.getLookControl().setLookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);
        if (!ReachHelper.canReach(golem, chestPos)) {
            moveToChest();
            return;
        }
        golem.deliverer.deliver(golem.level(), chestPos);
    }
}
