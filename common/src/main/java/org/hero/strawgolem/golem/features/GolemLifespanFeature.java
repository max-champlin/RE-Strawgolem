package org.hero.strawgolem.golem.features;

import net.minecraft.world.entity.ai.attributes.Attributes;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.golem.StrawGolem;

public class GolemLifespanFeature implements IGolemTickFeature {
    private int waitTime = 20;
    private int counter = 0;
    private StrawGolem golem;
    private boolean first = false;

    /**
     * Constructor for the Golem Life Span Feature
     * @param golem The Straw Golem the life span is bound to.
     */
    public GolemLifespanFeature(StrawGolem golem) {
        this.golem = golem;
    }

    /**
     * Spend a core from the golem's home lodge, if it has one with auto-retire
     * on. Returns true when the golem has been made immortal and must not die.
     */
    private boolean retireInTheField(org.hero.strawgolem.golem.StrawGolem golem) {
        if (golem.level().isClientSide) {
            return false;
        }
        net.minecraft.core.BlockPos home = golem.getHomePos();
        if (home == null) {
            return false;
        }
        // The lodge's block entity lives at the base of the building, and a
        // golem's home may name any storey of it.
        for (int down = 0; down < 3; down++) {
            net.minecraft.core.BlockPos p = home.below(down);
            if (golem.level().getBlockEntity(p)
                    instanceof org.hero.strawgolem.block.BunkhouseBlockEntity lodge
                    && lodge.retireOnDeath(golem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This tick method will increment a counter, and after twenty counter increments
     * the Straw Golem's life span will increment by one.
     */
    public void tick() {
        // This should never happen or be possible.
        if (golem == null) {
            Constants.LOG.error("Straw Golem null in: {}!", "Lifespan Feature");
            return;
        }
        if (golem.isImmortal()) {
            return; // stepped outside time
        }
        if (Constants.Golem.lifespan) {
            if (golem.getLifeSpan() >= Constants.Golem.maxLife) {
                // Ask the golem's own lodge for a core before killing it.
                //
                // Auto-retire used to run ONLY over the sleepers list, so it
                // could save a Master that happened to be in bed and nobody
                // else. Twenty-two Masters aged out mid-shift on 2026-09-04
                // with their cores sitting unspent in the apartment they were
                // homed to. Retirement has to be available at the moment of
                // death, not only at bedtime.
                if (retireInTheField(golem)) {
                    return;
                }
                // Kill golem if its lived past its maximum lifespan
                golem.kill();
                return;
            } else if (golem.getLifeSpan() < 0) {
                // Should never trigger, but better to be cautious
                // May have it simply kill the golem if it goes negative, but for now just reset.
                golem.setLifeSpan(0);
            }
            counter++;
            // Not efficient math, can address if necessary.
            if (counter >= (Constants.Golem.dynamicDecay ?
                    waitTime / (golem.getEnvironmentHarshness()) : waitTime)) {
                // If there's no variation continue as usual, otherwise give the golem a 90% to decay.
                if (!Constants.Golem.lifeVariation || golem.getRandom().nextFloat() < 0.9) {
                    // Increment life by 1 second.
                    golem.setLifeSpan(golem.getLifeSpan() + 1);
                }
                counter = 0;
            }
            updateGolemHealth();
        } else if (first) {
            first = false;
            var attr = golem.getAttribute(Attributes.MAX_HEALTH);
            if (attr != null) {
                // Normalizing healthRatio, since I don't want Golems over-healthed.
                attr.setBaseValue(Constants.Golem.maxHealth);
            }
        }
    }

    /**
     * This method updates the Straw Golem's health based on its lifespan.
     */
    public void updateGolemHealth() {
        // The casting probably could be simplified.
        // Using 3.0 to scale the speed, note that as lifespan -> maxLife the division should approach 1
        // Using 0.000001 as an epsilon, just to prevent any problems with imprecision.
        float epsilon = 0.000001f;
        int divisibility = 9;
        float healthRatio = (float) Math.floor(0.99 - divisibility
                * (double) golem.getLifeSpan() / Constants.Golem.maxLife);
        // Flipping the order, so that the closer life span is the max lifespan, the closer to 0.0f the health becomes.
        healthRatio += divisibility + epsilon;
        var attr = golem.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            // Normalizing healthRatio, since I don't want Golems over-healthed.
            attr.setBaseValue(Constants.Golem.maxHealth * Math.min(1.0f, healthRatio / divisibility));
        } else {
            // Should never trigger, but best to be safe.
            Constants.LOG.error("Golem missing Attribute: {}!", "Max Health");
        }
    }
    public void reset() {
        var attr = golem.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            // Normalizing healthRatio, since I don't want Golems over-healthed.
            attr.setBaseValue(Constants.Golem.maxHealth);
        } else {
            // Should never trigger, but best to be safe.
            Constants.LOG.error("Golem missing Attribute: {}!", "Max Health");
        }
    }
    public void refresh() {
        counter = 0;
        if (Constants.Golem.lifespan) {
            reset();
        } else {
            updateGolemHealth();
        }
    }
}
