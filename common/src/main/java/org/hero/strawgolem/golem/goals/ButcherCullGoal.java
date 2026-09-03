package org.hero.strawgolem.golem.goals;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thins overgrown herds. Counts the adults of each species in range and only
 * culls the ones above HERD_KEEP, so the stock always rebuilds itself (and the
 * Breeder golem has something left to work with). Drops are gathered straight
 * into the golem's satchel rather than left lying about as entities.
 */
public class ButcherCullGoal extends Goal {
    /** Adults of each species left standing. Four = two breeding pairs. */
    private static final int HERD_KEEP = 4;
    private static final int CULL_COOLDOWN = 200;
    private static final double WORK_DIST_SQ = 4.0;
    private static final int GATHER_TICKS = 20;

    private final StrawGolem golem;
    private Animal target;
    private int cooldown;
    private int gathering;

    public ButcherCullGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Livestock we are willing to put on the block. */
    private static boolean cullable(Animal animal) {
        return animal.isAlive()
                && !animal.isBaby()
                && !animal.hasCustomName()           // somebody named it; it's a pet
                && !animal.isLeashed()               // on a lead = spoken for
                && !(animal instanceof TamableAnimal tame && tame.isTame())
                && !animal.isInLove();               // let it finish breeding
    }

    private Animal findSurplus() {
        List<Animal> nearby = golem.level().getEntitiesOfClass(Animal.class,
                golem.getBoundingBox().inflate(Golem.searchRange));
        // Adults per species, so we only ever trim what's above the remnant.
        Map<EntityType<?>, Integer> adults = new HashMap<>();
        for (Animal animal : nearby) {
            if (!animal.isBaby() && animal.isAlive()) {
                adults.merge(animal.getType(), 1, Integer::sum);
            }
        }
        Animal best = null;
        double bestDist = Double.MAX_VALUE;
        for (Animal animal : nearby) {
            if (!cullable(animal) || adults.getOrDefault(animal.getType(), 0) <= HERD_KEEP
                    || !golem.mayWorkAt(animal.blockPosition())) {
                continue;
            }
            double dist = golem.distanceToSqr(animal);
            if (dist < bestDist) {
                bestDist = dist;
                best = animal;
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!golem.getMainHandItem().isEmpty()) {
            return false; // deliver what we're carrying first
        }
        target = findSurplus();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return gathering > 0 || (target != null && target.isAlive() && cullable(target));
    }

    @Override
    public void start() {
        gathering = 0;
        if (target != null) {
            golem.getNavigation().moveTo(target, Golem.defaultWalkSpeed);
        }
    }

    @Override
    public void stop() {
        target = null;
        gathering = 0;
        golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        // After the kill, sweep the drops into the satchel so they never sit
        // around as item entities.
        if (gathering > 0) {
            gathering--;
            for (ItemEntity item : golem.level().getEntitiesOfClass(ItemEntity.class,
                    golem.getBoundingBox().inflate(5.0))) {
                if (item.isAlive() && golem.stow(item.getItem())) {
                    item.discard();
                }
            }
            return;
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        golem.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (golem.distanceToSqr(target) > WORK_DIST_SQ) {
            if (golem.getNavigation().isDone()) {
                golem.getNavigation().moveTo(target, Golem.defaultWalkSpeed);
            }
            return;
        }
        golem.getNavigation().stop();
        golem.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        target.hurt(golem.level().damageSources().mobAttack(golem), target.getMaxHealth() + 10.0F);
        golem.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.NEUTRAL, 0.6F, 1.0F);
        golem.recordJob();
        target = null;
        gathering = GATHER_TICKS;
        cooldown = CULL_COOLDOWN;
    }
}
