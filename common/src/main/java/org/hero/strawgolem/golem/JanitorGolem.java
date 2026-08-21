package org.hero.strawgolem.golem;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.goals.BreederPickupGoal;
import org.hero.strawgolem.golem.goals.GolemStashGoal;
import org.hero.strawgolem.golem.goals.GolemWanderGoal;

/**
 * The cleanup crew: picks up every dropped item in range and carries it to the
 * bound chest. Pens, mob farms, creeper craters - he doesn't judge, he sweeps.
 */
public class JanitorGolem extends StrawGolem {

    public JanitorGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public boolean isEdibleGolem() {
        return false;
    }

    /** Cleans pens, so he must tolerate standing among livestock. */
    @Override
    protected void generateAvoids() {
        goalSelector.addGoal(1, new org.hero.strawgolem.golem.goals.GolemAvoidEntityGoal<>(this,
                net.minecraft.world.entity.monster.Pillager.class,
                (e) -> e.getTarget() instanceof StrawGolem,
                Golem.fleeRange, Golem.defaultWalkSpeed, Golem.defaultRunSpeed,
                net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new PanicGoal(this, Golem.defaultRunSpeed * 1.2));
        generateAvoids();
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemGoHomeGoal(this));
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemEatGoal(this));
        goalSelector.addGoal(1, new BreederPickupGoal(this));
        goalSelector.addGoal(1, new GolemStashGoal(this, s -> false, 10));
        goalSelector.addGoal(2, new GolemWanderGoal(this));
    }
}
