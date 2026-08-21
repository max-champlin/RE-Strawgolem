package org.hero.strawgolem.golem;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.goals.ButcherCullGoal;
import org.hero.strawgolem.golem.goals.GolemDepositGoal;
import org.hero.strawgolem.golem.goals.GolemWanderGoal;

/**
 * The other half of the Breeder. Culls surplus adult livestock down to a
 * breeding remnant, pockets the meat and hides, and takes them to the chest.
 * Never touches babies, named animals, tamed pets or anything on a lead - a
 * herd it has thinned will always grow back.
 */
public class ButcherGolem extends StrawGolem {

    public ButcherGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public boolean isEdibleGolem() {
        return false;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new PanicGoal(this, Golem.defaultRunSpeed * 1.2));
        generateAvoids();
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemGoHomeGoal(this));
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemEatGoal(this));
        goalSelector.addGoal(1, new ButcherCullGoal(this));
        goalSelector.addGoal(1, new GolemDepositGoal(this));
        goalSelector.addGoal(2, new GolemWanderGoal(this));
    }
}
