package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.platform.Services;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Fetches the right tool for the job from the milkmaid's chest: a bowl when a
 * mooshroom is about (stew beats milk), a bucket when a cow needs milking, or
 * shears when something woolly is ready.
 */
public class MilkmaidFetchGoal extends Goal {
    private static final Predicate<ItemStack> BUCKET = s -> s.is(Items.BUCKET);
    private static final Predicate<ItemStack> BOWL = s -> s.is(Items.BOWL);
    private static final Predicate<ItemStack> SHEARS = s -> s.getItem() instanceof ShearsItem;

    private final StrawGolem golem;
    private Predicate<ItemStack> wanted;
    private boolean done;

    public MilkmaidFetchGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static Cow findMilkable(StrawGolem golem) {
        return golem.level().getEntitiesOfClass(Cow.class,
                        golem.getBoundingBox().inflate(Golem.searchRange),
                        c -> c.isAlive() && !c.isBaby() && golem.mayWorkAt(c.blockPosition()))
                .stream().min(Comparator.comparingDouble(golem::distanceToSqr)).orElse(null);
    }

    public static MushroomCow findStewable(StrawGolem golem) {
        return golem.level().getEntitiesOfClass(MushroomCow.class,
                        golem.getBoundingBox().inflate(Golem.searchRange),
                        c -> c.isAlive() && !c.isBaby() && golem.mayWorkAt(c.blockPosition()))
                .stream().min(Comparator.comparingDouble(golem::distanceToSqr)).orElse(null);
    }

    private Predicate<ItemStack> pickWanted() {
        if (findStewable(golem) != null && chestHas(BOWL)) {
            return BOWL;
        }
        if (findMilkable(golem) != null && chestHas(BUCKET)) {
            return BUCKET;
        }
        if (BreederShearGoal.findShearable(golem) != null && chestHas(SHEARS)) {
            return SHEARS;
        }
        return null;
    }

    private boolean chestHas(Predicate<ItemStack> pred) {
        Level level = golem.level();
        BlockPos chest = golem.getPriorityPos();
        if (level.getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (pred.test(container.getItem(i))) {
                    return true;
                }
            }
            return false;
        }
        return !Services.PLATFORM.extractMatching(level, chest, pred, 1, true).isEmpty();
    }

    @Override
    public boolean canUse() {
        if (!golem.getMainHandItem().isEmpty()
                || golem.getPriorityPos().getX() == Integer.MAX_VALUE
                || !ContainerHelper.isContainer(golem, golem.getPriorityPos())) {
            return false;
        }
        wanted = pickWanted();
        return wanted != null;
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
        wanted = null;
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
        ItemStack got = withdraw(wanted);
        if (!got.isEmpty()) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, got);
        }
        done = true;
    }

    private ItemStack withdraw(Predicate<ItemStack> pred) {
        Level level = golem.level();
        BlockPos chest = golem.getPriorityPos();
        if (level.getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (pred.test(container.getItem(i))) {
                    return container.removeItem(i, 1);
                }
            }
            return ItemStack.EMPTY;
        }
        return Services.PLATFORM.extractMatching(level, chest, pred, 1, false);
    }
}
