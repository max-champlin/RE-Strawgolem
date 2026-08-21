package org.hero.strawgolem.golem.goals;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.EnumSet;

/**
 * Bucket in hand -> milk from the nearest cow. Bowl in hand -> stew from the
 * nearest mooshroom. A cooldown paces the dairy so it feels like chores, not a
 * pump.
 */
public class MilkmaidMilkGoal extends Goal {
    private static final double WORK_DIST_SQ = 4.0;
    private static final int MILK_COOLDOWN = 100;

    private final StrawGolem golem;
    private Cow target;
    private int cooldown;

    public MilkmaidMilkGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        ItemStack hand = golem.getMainHandItem();
        if (hand.is(Items.BOWL)) {
            target = MilkmaidFetchGoal.findStewable(golem);
        } else if (hand.is(Items.BUCKET)) {
            target = MilkmaidFetchGoal.findMilkable(golem);
        } else {
            target = null;
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive()
                && (golem.getMainHandItem().is(Items.BUCKET) || golem.getMainHandItem().is(Items.BOWL));
    }

    @Override
    public void start() {
        golem.getNavigation().moveTo(target, Golem.defaultWalkSpeed);
    }

    @Override
    public void stop() {
        target = null;
        golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            return;
        }
        golem.getLookControl().setLookAt(target);
        if (golem.distanceToSqr(target) > WORK_DIST_SQ) {
            if (golem.getNavigation().isDone()) {
                golem.getNavigation().moveTo(target, Golem.defaultWalkSpeed);
            }
            return;
        }
        ItemStack hand = golem.getMainHandItem();
        if (hand.is(Items.BOWL) && target instanceof MushroomCow) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MUSHROOM_STEW));
            golem.level().playSound(null, target.blockPosition(), SoundEvents.MOOSHROOM_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (hand.is(Items.BUCKET)) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MILK_BUCKET));
            golem.level().playSound(null, target.blockPosition(), SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        cooldown = MILK_COOLDOWN;
        target = null;
    }
}
