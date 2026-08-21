package org.hero.strawgolem.golem;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.goals.GardenerFeedGoal;
import org.hero.strawgolem.golem.goals.GardenerFetchGoal;
import org.hero.strawgolem.golem.goals.GolemStashGoal;
import org.hero.strawgolem.golem.goals.GolemWanderGoal;

/**
 * Support staff for the farm: carries bonemeal from its chest and sprinkles it
 * on anything that grows - crops, saplings, flower beds. Never harvests; just
 * makes everyone else's day shorter.
 */
public class GardenerGolem extends StrawGolem {

    public GardenerGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
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
        goalSelector.addGoal(1, new GardenerFetchGoal(this));
        goalSelector.addGoal(1, new GardenerFeedGoal(this));
        goalSelector.addGoal(2, new GolemStashGoal(this,
                s -> s.is(Items.BONE_MEAL) && GardenerFeedGoal.findTarget(this) != null, 60));
        goalSelector.addGoal(2, new GolemWanderGoal(this));
    }
}
