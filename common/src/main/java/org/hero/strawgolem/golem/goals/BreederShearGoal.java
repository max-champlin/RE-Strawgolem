package org.hero.strawgolem.golem.goals;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * When the breeder golem is holding shears, it shears any nearby animal that is
 * ready (sheep regrown wool, etc.). The dropped wool is collected by the pickup
 * goal and returned to the chest by the stash goal - a complete wool pipeline.
 */
public class BreederShearGoal extends Goal {
    private static final double SHEAR_DIST_SQ = 4.0;

    /**
     * Ticks between path recalculations while closing on the animal.
     *
     * <p>Shorter than the interval the container goals use, because the target
     * wanders off under its own power - but not every tick, which is what this
     * did before and which meant a full A* search per golem per tick.
     */
    private static final int REPATH_INTERVAL = 10;

    private final StrawGolem golem;
    private Animal target;
    private int repathTicks;

    public BreederShearGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean holdingShears(StrawGolem golem) {
        return golem.getMainHandItem().getItem() instanceof ShearsItem;
    }

    public static Animal findShearable(StrawGolem golem) {
        return golem.level().getEntitiesOfClass(Animal.class,
                        golem.getBoundingBox().inflate(Golem.searchRange),
                        a -> a.isAlive() && a instanceof Shearable sh && sh.readyForShearing()
                                && golem.mayWorkAt(a.blockPosition()))
                .stream().min(Comparator.comparingDouble(golem::distanceToSqr)).orElse(null);
    }

    @Override
    public boolean canUse() {
        if (!holdingShears(golem)) {
            return false;
        }
        target = findShearable(golem);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && holdingShears(golem)
                && target instanceof Shearable sh && sh.readyForShearing();
    }

    @Override
    public void start() {
        golem.getNavigation().moveTo(target, Golem.defaultWalkSpeed);
        repathTicks = REPATH_INTERVAL;
    }

    @Override
    public void stop() {
        target = null;
        golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        golem.getLookControl().setLookAt(target);
        if (golem.distanceToSqr(target) > SHEAR_DIST_SQ) {
            if (repathTicks-- <= 0 || golem.getNavigation().isDone()) {
                repathTicks = REPATH_INTERVAL;
                golem.getNavigation().moveTo(target, Golem.defaultWalkSpeed);
            }
            return;
        }
        if (target instanceof Shearable sh && sh.readyForShearing()) {
            sh.shear(SoundSource.NEUTRAL);
            ItemStack shears = golem.getMainHandItem();
            shears.hurtAndBreak(1, golem, EquipmentSlot.MAINHAND);
        }
        target = null;
    }
}
