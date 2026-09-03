package org.hero.strawgolem.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.goals.BrewerCollectGoal;
import org.hero.strawgolem.golem.goals.BrewerSupplyGoal;
import org.hero.strawgolem.golem.goals.GolemStashGoal;
import org.hero.strawgolem.golem.goals.GolemWanderGoal;
import org.hero.strawgolem.platform.Services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Tends every brewing stand in range: keeps them stocked with blaze powder,
 * water bottles, and whatever ingredient from its chest actually brews with the
 * bottles inside, then collects the finished potions.
 */
public class BrewerGolem extends StrawGolem {

    public BrewerGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public boolean isEdibleGolem() {
        return false;
    }


    /** All brewing stands in working range, nearest first. */
    public List<BlockPos> findStands() {
        BlockPos center = blockPosition();
        int r = Golem.searchRange;
        int rv = Golem.searchRangeVertical;
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -rv, -r), center.offset(r, rv, r))) {
            if (level().getBlockEntity(pos) instanceof BrewingStandBlockEntity && mayWorkAt(pos)) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(getX(), getY(), getZ())));
        return found;
    }

    public static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION)
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    /** Whether any ingredient in the chest can brew this bottle further. */
    public boolean chestCanBrew(ItemStack bottle) {
        return chestHas(s -> level().potionBrewing().hasMix(bottle, s));
    }

    /** Withdraws the first matching stack from the bound chest. */
    public ItemStack takeFromChest(Predicate<ItemStack> predicate, int max) {
        Level level = level();
        BlockPos chest = getPriorityPos();
        if (chest.getX() == Integer.MAX_VALUE) {
            return ItemStack.EMPTY;
        }
        if (level.getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (predicate.test(slot)) {
                    return container.removeItem(i, Math.min(max, slot.getCount()));
                }
            }
            return ItemStack.EMPTY;
        }
        return Services.PLATFORM.extractMatching(level, chest, predicate, max, false);
    }

    /** Whether the bound chest holds a matching stack. */
    public boolean chestHas(Predicate<ItemStack> predicate) {
        Level level = level();
        BlockPos chest = getPriorityPos();
        if (chest.getX() == Integer.MAX_VALUE) {
            return false;
        }
        if (level.getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (predicate.test(container.getItem(i))) {
                    return true;
                }
            }
            return false;
        }
        return !Services.PLATFORM.extractMatching(level, chest, predicate, 1, true).isEmpty();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new PanicGoal(this, Golem.defaultRunSpeed * 1.2));
        generateAvoids();
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemGoHomeGoal(this));
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemEatGoal(this));
        goalSelector.addGoal(1, new BrewerCollectGoal(this));
        goalSelector.addGoal(1, new BrewerSupplyGoal(this));
        goalSelector.addGoal(2, new GolemStashGoal(this, s -> false, 20));
        goalSelector.addGoal(2, new GolemWanderGoal(this));
    }
}
