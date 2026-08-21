package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.platform.Services;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * When something nearby wants fertilizing and the gardener's hand is empty,
 * fetches a handful of bonemeal from the bound chest.
 */
public class GardenerFetchGoal extends Goal {
    private static final Predicate<ItemStack> BONE_MEAL = s -> s.is(Items.BONE_MEAL);
    private static final int GRAB_COUNT = 16;

    private final StrawGolem golem;
    private boolean done;

    public GardenerFetchGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean chestHasBonemeal() {
        Level level = golem.level();
        BlockPos chest = golem.getPriorityPos();
        if (level.getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (BONE_MEAL.test(container.getItem(i))) {
                    return true;
                }
            }
            return false;
        }
        return !Services.PLATFORM.extractMatching(level, chest, BONE_MEAL, 1, true).isEmpty();
    }

    @Override
    public boolean canUse() {
        return golem.getMainHandItem().isEmpty()
                && golem.getPriorityPos().getX() != Integer.MAX_VALUE
                && ContainerHelper.isContainer(golem, golem.getPriorityPos())
                && GardenerFeedGoal.findTarget(golem) != null
                && chestHasBonemeal();
    }

    @Override
    public boolean canContinueToUse() {
        return !done && golem.getMainHandItem().isEmpty()
                && ContainerHelper.isContainer(golem, golem.getPriorityPos());
    }

    @Override
    public void start() {
        done = false;
        moveToChest();
    }

    @Override
    public void stop() {
        done = false;
        golem.getNavigation().stop();
    }

    private void moveToChest() {
        BlockPos chest = golem.getPriorityPos();
        golem.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        BlockPos chest = golem.getPriorityPos();
        golem.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
        if (!ReachHelper.canReach(golem, chest)) {
            moveToChest();
            return;
        }
        ItemStack got = withdraw();
        if (!got.isEmpty()) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, got);
        }
        done = true;
    }

    private ItemStack withdraw() {
        Level level = golem.level();
        BlockPos chest = golem.getPriorityPos();
        if (level.getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (BONE_MEAL.test(slot)) {
                    return container.removeItem(i, Math.min(GRAB_COUNT, slot.getCount()));
                }
            }
            return ItemStack.EMPTY;
        }
        return Services.PLATFORM.extractMatching(level, chest, BONE_MEAL, GRAB_COUNT, false);
    }
}
