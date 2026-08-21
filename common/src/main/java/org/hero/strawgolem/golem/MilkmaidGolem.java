package org.hero.strawgolem.golem;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.goals.BreederShearGoal;
import org.hero.strawgolem.golem.goals.GolemStashGoal;
import org.hero.strawgolem.golem.goals.GolemWanderGoal;
import org.hero.strawgolem.golem.goals.MilkmaidFetchGoal;
import org.hero.strawgolem.golem.goals.MilkmaidMilkGoal;

/**
 * The action-harvester of the pen: milks cows (buckets from her chest), bowls
 * stew from mooshrooms, and shears anything woolly. The Breeder breeds, the
 * Milkmaid collects, the Janitor sweeps - proper division of labor.
 */
public class MilkmaidGolem extends StrawGolem {

    public MilkmaidGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public boolean isEdibleGolem() {
        return false;
    }

    /** Works among livestock, so only Pillagers spook her. */
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
        goalSelector.addGoal(1, new MilkmaidFetchGoal(this));
        goalSelector.addGoal(1, new MilkmaidMilkGoal(this));
        goalSelector.addGoal(1, new BreederShearGoal(this));
        goalSelector.addGoal(2, new GolemStashGoal(this, this::toolStillUseful, 40));
        goalSelector.addGoal(2, new GolemWanderGoal(this));
    }

    /** Keep a tool in hand while there is still work for it nearby. */
    private boolean toolStillUseful(net.minecraft.world.item.ItemStack stack) {
        if (stack.is(Items.BUCKET)) {
            return MilkmaidFetchGoal.findMilkable(this) != null;
        }
        if (stack.is(Items.BOWL)) {
            return MilkmaidFetchGoal.findStewable(this) != null;
        }
        if (stack.getItem() instanceof ShearsItem) {
            return BreederShearGoal.findShearable(this) != null;
        }
        return false;
    }
}
