package org.hero.strawgolem.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.hero.strawgolem.client.GolemArmAnimationController;
import org.hero.strawgolem.client.GolemHarvestAnimationController;
import org.hero.strawgolem.client.GolemLegAnimationController;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.golem.api.BiPredicate;
import org.hero.strawgolem.golem.api.VisionHelper;
import org.hero.strawgolem.golem.features.GolemHungerFeature;
import org.hero.strawgolem.golem.features.GolemLifespanFeature;
import org.hero.strawgolem.golem.features.IGolemTickFeature;
import org.hero.strawgolem.golem.goals.*;
import org.hero.strawgolem.mixinInterfaces.GolemOrderer;
import org.hero.strawgolem.registry.ItemRegistry;
import org.hero.strawgolem.registry.SoundRegistry;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.hero.strawgolem.Constants.*;

public class StrawGolem extends AbstractGolem implements GeoAnimatable {
    // Constructor for Straw Golem just uses the super class (needs to be examined for changes in future versions).
    public StrawGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    // GeckoLib variable, unnoteworthy.
    private final AnimatableInstanceCache instanceCache = GeckoLibUtil.createInstanceCache(this);

    // The deliverer for the Straw Golem.
    public final Deliverer deliverer = new Deliverer();

    // The features of the Straw Golem.
    private final GolemHungerFeature hunger = new GolemHungerFeature(this);
    private final GolemLifespanFeature lifeSpan = new GolemLifespanFeature(this);
    private final List<IGolemTickFeature> features = List.of(hunger, lifeSpan);

    // The constants of the Straw Golem.
    public static final double defaultMovement = Golem.defaultMovement;
    public static final double defaultWalkSpeed = Golem.defaultWalkSpeed;
    public static final float baseHealth = Golem.maxHealth;
    // Synched data accessors for the Straw Golem.
    private static final EntityDataAccessor<Boolean> HAT = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BOOLEAN);
    /** Display-only mirrors of server-side state, for the client's directory. */
    /** Body material. Synched because the client picks the texture from it. */
    private static final EntityDataAccessor<Integer> MATERIAL =
            SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> JOBS_SYNC =
            SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BAG_USED =
            SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> HOME_SYNC =
            SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BLOCK_POS);
    /** Synched so the renderer can show a pack, and so clients agree on capacity. */
    private static final EntityDataAccessor<Boolean> BACKPACK = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FESTIVE = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PANIC = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CARRY_STATUS = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PICKUP_STATUS = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BARREL = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HUNGER = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_SPAN = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> PRIORITY_POS = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BLOCK_POS);
    /**
     * Where this golem PICKS UP from, when that differs from where it drops off.
     * Unset (sentinel MAX_VALUE) means "same as priorityPos", which is how every
     * golem behaved before - so nothing changes until a Traffic Cone says so.
     */
    private static final EntityDataAccessor<BlockPos> PICKUP_POS = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);
    /**
     * Crew colour: 0 for undyed, otherwise DyeColor ordinal + 1.
     *
     * <p>A key, never a job. Colour decides which work orders a golem is
     * ELIGIBLE for and nothing else - it cannot change what the golem is
     * capable of, because that is its class.
     *
     * <p>Synched because the badge and the Foreman's Clipboard are both drawn
     * client-side, and a crew you cannot see at a glance is not a crew.
     */
    private static final EntityDataAccessor<Integer> CREW_COLOUR =
            SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> IMMORTAL = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.BOOLEAN);
    /** Client-visible label of the crop(s) this golem is assigned to ("" = any). */
    private static final EntityDataAccessor<String> ASSIGNMENT = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.STRING);
    /**
     * The name a golem is born with. Synched because the nameplate and the
     * Foreman's Clipboard are both drawn client-side, and stored rather than
     * derived from the UUID because {@link org.hero.strawgolem.item.GolemRetrainerItem#convert}
     * builds a brand new entity - a derived name would change every hat swap,
     * retrain, stick refresh and self-heal.
     */
    private static final EntityDataAccessor<String> BIRTH_NAME = SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.STRING);
    /** Who hired this golem. Synched so the roster can say "yours". */
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> OWNER =
            SynchedEntityData.defineId(StrawGolem.class, EntityDataSerializers.OPTIONAL_UUID);

    // Variable for forcing Straw Golem animation resets.
    private boolean forceAnimationReset = false;
    // Variable for determining whether Straw Golem is creating snow particles.
    public boolean createSnow = false;

    /**
     * Use a navigator that refuses to repeat a search that just failed.
     *
     * <p>The goals in this mod overwhelmingly path with
     * {@code if (getNavigation().isDone()) moveTo(target)}, which throttles
     * itself only while the path succeeds. Against an unreachable target it runs
     * a full A* every tick indefinitely - measured at 12,258 seconds on one
     * golem. {@link GolemNavigation} caps that centrally instead of asking
     * twenty-eight goals to each remember to.
     */
    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(
            net.minecraft.world.level.Level level) {
        return new GolemNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        // Adds goals for the Straw Golem.
        goalSelector.addGoal(0, new FloatGoal(this));
        // Multiplying by 1.2 as a sort of "adrenaline" factor.
        goalSelector.addGoal(0, new PanicGoal(this, Golem.defaultRunSpeed * 1.2));
        // Adds the Avoidance goals.
        generateAvoids();
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemGoHomeGoal(this));
        // Priority 0: hunger outranks work. At the same priority as harvest and
        // deposit it could never interrupt a golem mid-delivery, so a starving
        // golem on a long haul just kept starving. Float/Panic still come first.
        goalSelector.addGoal(0, new org.hero.strawgolem.golem.goals.GolemEatGoal(this));
        goalSelector.addGoal(2, new GolemWanderGoal(this));
        goalSelector.addGoal(1, new GolemDepositGoal(this));
        goalSelector.addGoal(1, new GolemHarvestGoal(this));
        goalSelector.addGoal(1, new GolemGrabGoal(this));
    }

    /**
     * Generates and adds to the goalSelector the Avoidance goals
     * of the entities that the Straw Golem wants to avoid.
     */
    protected void generateAvoids() {
        final double WALK = defaultWalkSpeed;
        final double RUN = Golem.defaultRunSpeed;
        // Just a variable to store priority conveniently.
        final int PRIORITY = 1;
        // Adding the avoid entity goals, could make this a for-each loop, but it seems unneeded.
        goalSelector.addGoal(PRIORITY, new GolemAvoidEntityGoal<>(this, Pillager.class,
                (e) -> e.getTarget() instanceof StrawGolem, Golem.fleeRange, WALK, RUN, EntitySelector.NO_CREATIVE_OR_SPECTATOR));
        goalSelector.addGoal(PRIORITY, new GolemAvoidEntityGoal<>(this, Sheep.class,
                (e) -> e.getTarget() instanceof StrawGolem, Golem.fleeRange, WALK, RUN, EntitySelector.NO_CREATIVE_OR_SPECTATOR));
        goalSelector.addGoal(PRIORITY, new GolemAvoidEntityGoal<>(this, Cow.class,
                (e) -> e.getTarget() instanceof StrawGolem, Golem.fleeRange, WALK, RUN, EntitySelector.NO_CREATIVE_OR_SPECTATOR));
        goalSelector.addGoal(PRIORITY, new GolemAvoidEntityGoal<>(this, Pig.class,
                (e) -> e.getTarget() instanceof StrawGolem, Golem.fleeRange, WALK, RUN, EntitySelector.NO_CREATIVE_OR_SPECTATOR));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder pBuilder) {
        super.defineSynchedData(pBuilder);
        // Defines any required persistent and synched data.
        pBuilder.define(CARRY_STATUS, 0);
        pBuilder.define(PICKUP_STATUS, 0);
        pBuilder.define(HAT, false);
        pBuilder.define(BACKPACK, false);
        pBuilder.define(FESTIVE, false);
        pBuilder.define(PANIC, false);
        pBuilder.define(BARREL, 0);
        pBuilder.define(HUNGER, 0);
        pBuilder.define(LIFE_SPAN, 0);
        pBuilder.define(PRIORITY_POS, new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        pBuilder.define(PICKUP_POS, new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        pBuilder.define(CREW_COLOUR, 0);
        pBuilder.define(MATERIAL, 0);
        pBuilder.define(JOBS_SYNC, 0);
        pBuilder.define(BAG_USED, 0);
        pBuilder.define(HOME_SYNC, NO_POS);
        pBuilder.define(RANK, 0);
        pBuilder.define(IMMORTAL, false);
        pBuilder.define(ASSIGNMENT, "");
        pBuilder.define(BIRTH_NAME, "");
        pBuilder.define(OWNER, java.util.Optional.empty());
    }
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new GolemArmAnimationController(this));
        registrar.add(new GolemLegAnimationController(this));
        registrar.add(new GolemHarvestAnimationController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return instanceCache;
    }

    @Override
    public double getTick(Object o) {
        return RenderUtil.getCurrentTick();
    }

    /**
     * Creates the Straw Golem's Attributes.
     * @return The Straw Golem's Attributes.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, defaultMovement)
                .add(Attributes.MAX_HEALTH, baseHealth)
                // FOLLOW_RANGE drives how far the pathfinder searches. It was
                // 48 (~searchRange*2), which made EVERY path calc and every
                // canPath crop-check grind a 48-block sphere - pathfinding cost
                // scales ~cubically, so 15 golems ate 12% of the server tick.
                // Worse, on an UNREACHABLE target the pathfinder chewed that
                // huge area before giving up, so a golem wedged instead of
                // bailing (only GoHomeGoal at night could break it). Just past
                // the search radius is plenty to reach anything the golem finds.
                .add(Attributes.FOLLOW_RANGE, Math.max(16.0, Golem.searchRange + 8.0));
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel pLevel, DamageSource pDamageSource) {
        super.dropAllDeathLoot(pLevel, pDamageSource);
        if (hasHat()) {
            this.spawnAtLocation(ItemRegistry.STRAW_HAT.get());
        }
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        ItemStack itemstack = this.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!itemstack.isEmpty()) {
            this.spawnAtLocation(itemstack);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    @Override
    public void tick() {
        // Tick each feature
        // May need to push all of these onto serverSide
        if (!level().isClientSide && isAlive())  {
            if (random.nextFloat() < 0.02f) {
                playSound(SoundRegistry.GOLEM_AMBIENT.get());
            }
            if (isImmortal() && random.nextFloat() < 0.01f
                    && level() instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        getX(), getY() + 0.6, getZ(), 2, 0.2, 0.3, 0.2, 0.005);
            }
            features.forEach(IGolemTickFeature::tick);
            if (Golem.panic) {
                setPanic(isRunningScaredGoal());
                if (getPanic()) {
                    dropEquipment();
                }
            }
        }
        // Get the Straw Golem's held item.
        Item item = getMainHandItem().getItem();

        // Refresh the Straw Golem's carry status based on held item.

        // If the Straw Golem is holding a Block.
        if (item instanceof BlockItem && !(item instanceof ItemNameBlockItem)) setCarryStatus(2);
        // If the Straw Golem is holding a regular item.
        else if (!getMainHandItem().isEmpty()) setCarryStatus(1);
        // If the Straw Golem is holding nothing.
        else setCarryStatus(0);
        super.tick();
        if (!level().isClientSide && isAlive()) {
            // Named lazily rather than at spawn so golems ALREADY in the world
            // get one on next load, instead of only new hires.
            if (getBirthName().isEmpty()) {
                setBirthName(GolemNames.generate(random));
            }
            // Mirror the server-only fields the directory shows. SynchedEntityData
            // only sends on change, so re-setting an unchanged value is free -
            // and doing it here covers the satchel being drained by direct list
            // operations in the deposit goal that no setter can intercept.
            entityData.set(JOBS_SYNC, jobsDone);
            entityData.set(BAG_USED, satchel.size());
            entityData.set(HOME_SYNC, homePos == null ? NO_POS : homePos);
            tickWatch();
            tickOrder();
            // Slow roster refresh - the directory only needs a position good to
            // within half a minute, and this runs for every golem in the world.
            if (tickCount % org.hero.strawgolem.network.GolemRegistry.UPDATE_INTERVAL == 0) {
                updateRoster(org.hero.strawgolem.network.RosterEntry.STATE_WORKING);
            }
        }
        // Self-heal watchdog REMOVED 2026-07-27: it mass-rebuilt golems that
        // were only transiently frozen by a server-lag spike (they recover on
        // their own once the tick catches up). Rebuilding an entity is costly,
        // so a spike -> mass-rebuild -> worse lag -> more freezes became a death
        // spiral that tanked the server. Frozen golems now just wait out the
        // lag; the Foreman's Stick Refresh mode handles the rare truly-corrupt
        // one by hand. (tickStuckWatchdog/selfHeal kept below but uncalled.)
    }

    // --- GolemWatch: permanent stuck detection. See GolemWatch for the design.
    // Detection is deliberately trivial (a position compare once a second); all
    // the expensive context gathering happens in the report, which only fires
    // when a golem has been motionless for 30 seconds. A healthy crew logs
    // nothing at all.
    private int watchTicks = 0;
    private net.minecraft.world.phys.Vec3 watchLastPos = null;
    private boolean watchReported = false;

    /**
     * Set just before an INTENTIONAL discard (bunkhouse check-in, bindle
     * capture, retrain/refresh rebuild) so the watch stays quiet about it.
     * Not saved to NBT - it only has to survive the few lines between being
     * set and the entity going away.
     */
    private boolean expectedRemoval = false;

    /** Call immediately before {@code discard()} when the removal is deliberate. */
    public void markExpectedRemoval() {
        this.expectedRemoval = true;
    }

    /**
     * Writes this golem's line into the persistent roster.
     *
     * <p>Called on a slow tick and at the moments a golem changes state, so the
     * Employee Directory can list golems that are unloaded, asleep in a
     * bunkhouse, or packed in a bindle - none of which exist as entities for
     * anything to scan.
     *
     * @param state one of the RosterEntry STATE_ constants
     */
    public void updateRoster(String state) {
        if (level().isClientSide || level().getServer() == null) {
            return;
        }
        org.hero.strawgolem.network.GolemRegistry reg =
                org.hero.strawgolem.network.GolemRegistry.get(level().getServer());
        if (reg == null) {
            return;
        }
        reg.put(getUUID(), getOwnerUUID().orElse(null), new org.hero.strawgolem.network.RosterEntry(
                getUUID(),
                // Name tag wins, birth name otherwise - a golem asleep in a far
                // bunkhouse should read the same in the Clipboard as it does
                // standing in front of you.
                displayName(),
                getClass().getSimpleName(),
                getRank(),
                isImmortal(),
                getHunger(),
                state,
                level().dimension().location().toString(),
                blockPosition(),
                level().getGameTime(),
                crewId()));
    }

    /** Drops this golem from the roster - it is gone for good. */
    private void clearFromRoster() {
        if (level().isClientSide || level().getServer() == null) {
            return;
        }
        org.hero.strawgolem.network.GolemRegistry reg =
                org.hero.strawgolem.network.GolemRegistry.get(level().getServer());
        if (reg != null) {
            reg.remove(getUUID());
        }
    }

    /**
     * Catches golems that leave WITHOUT dying and WITHOUT anything asking them
     * to - the genuinely unexplained disappearances.
     *
     * <p>Bunkhouse check-in discards and re-creates every golem every night, so
     * logging bare DISCARDED buried the log in ~49 lines of normal cycling.
     * Only unannounced removals are interesting. Chunk unloads and dimension
     * changes are normal churn and stay silent too.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason == RemovalReason.DISCARDED
                && isAlive() && !expectedRemoval) {
            GolemWatch.reportGone(this, "VANISHED", "discarded with no known cause");
            clearFromRoster();
        }
        super.remove(reason);
    }

    /**
     * Per-trade, per-individual voice.
     *
     * <p>Every golem sound goes through {@code playSound(SoundEvent)}, which
     * vanilla routes through this method - so one override re-pitches ambience,
     * hurt, death, happy, interested and strained in one go, using the 33 sound
     * files the mod already ships. No new audio needed.
     *
     * <p>Two layers: a base pitch giving each TRADE a character (the Butcher
     * and Smelter growl, the Beekeeper and Milkmaid chirp), plus a small offset
     * derived from the golem's UUID so two Cooks standing together don't sound
     * like the same voice played twice. The UUID is stable, so a golem keeps its
     * voice across reloads - and across the bunkhouse rebuilding it at dawn,
     * which changes the entity id but never the UUID.
     */
    @Override
    public float getVoicePitch() {
        float base = switch (getClass().getSimpleName()) {
            case "ButcherGolem", "SmelterGolem" -> 0.80F;
            case "MetalworkerGolem", "LumberjackGolem" -> 0.86F;
            case "MinerGolem", "ExcavatorGolem" -> 0.90F;
            case "FisherGolem", "StockGolem" -> 0.96F;
            case "JanitorGolem", "BrewerGolem" -> 1.05F;
            case "ArtisanGolem", "BreederGolem" -> 1.12F;
            case "CookGolem", "GardenerGolem" -> 1.16F;
            case "BeekeeperGolem", "MilkmaidGolem" -> 1.22F;
            default -> 1.00F; // plain Harvester
        };
        // +/- 0.06 of individual character, stable per golem.
        float variance = ((getUUID().hashCode() & 0xFF) / 255.0F - 0.5F) * 0.12F;
        // Minecraft clamps playback to 0.5 - 2.0; stay well inside it.
        return Math.max(0.6F, Math.min(1.5F, base + variance));
    }

    /** Names of every goal currently running, '+'-joined. Empty means NOTHING is running. */
    public String runningGoalNames() {
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : goalSelector.getAvailableGoals()) {
            if (wrapped.isRunning()) {
                if (sb.length() > 0) {
                    sb.append('+');
                }
                sb.append(wrapped.getGoal().getClass().getSimpleName());
            }
        }
        return sb.toString();
    }

    private void tickWatch() {
        if (tickCount % GolemWatch.SAMPLE_INTERVAL != 0) {
            return;
        }
        // Riding, panicking or asleep-in-a-bunkhouse golems are legitimately
        // still; don't accuse them.
        if (getPanic() || isPassenger() || isNoAi()) {
            watchTicks = 0;
            watchReported = false;
            watchLastPos = position();
            return;
        }
        // IDLE IS NOT STUCK. A golem with an empty hand and no goal running has
        // nothing it is failing at - it is waiting for work. That happens
        // legitimately indoors, where RandomStrollGoal often finds no valid
        // target and so does not run at all, leaving the golem standing.
        //
        // Reporting those was most of what the watch said: the same artisan
        // logged STUCK/RECOVERED every evening for months while doing nothing
        // wrong, and that noise is what a real wedge has to be spotted against.
        // "Stuck" now means TRYING AND FAILING - carrying something it cannot
        // put down, or running a goal that is getting nowhere.
        if (getMainHandItem().isEmpty() && runningGoalNames().isEmpty()) {
            watchTicks = 0;
            watchReported = false;
            watchLastPos = position();
            return;
        }
        if (watchLastPos == null) {
            watchLastPos = position();
            return;
        }
        if (position().distanceToSqr(watchLastPos) > GolemWatch.MOVE_EPS_SQ) {
            if (watchReported) {
                GolemWatch.reportRecovered(this, watchTicks);
            }
            watchTicks = 0;
            watchReported = false;
            watchLastPos = position();
            return;
        }
        watchTicks += GolemWatch.SAMPLE_INTERVAL;
        // ONE report per episode - no repeating spam while it stays wedged.
        if (!watchReported && watchTicks >= GolemWatch.STUCK_AFTER) {
            watchReported = true;
            GolemWatch.reportStuck(this, watchTicks);
        }
    }

    private int stuckTicks = 0;
    private net.minecraft.world.phys.Vec3 lastCheckPos = null;
    // A healthy golem ALWAYS drifts (the wander goal moves it even with no
    // work). Staying pinned inside half a block for this long means its
    // navigation is dead - the frozen-golem corruption. ~90s is deliberately
    // conservative so a legitimately-idle golem is never rebuilt by mistake.
    private static final int STUCK_HEAL_TICKS = 1800;

    /**
     * Self-healing watchdog: a golem whose navigation has silently died just
     * stands there forever (empty-handed, or holding goods it can't deliver).
     * We detect that as "hasn't moved at all for ~90s" and rebuild the entity
     * clean - which keeps rank, soul, home, hat, name and trained filters, so
     * the fix is invisible. Replaces having to bop a frozen golem by hand.
     */
    private void tickStuckWatchdog() {
        if (tickCount % 40 != 0) {
            return; // sample every 2s
        }
        // Don't touch golems that are legitimately not roaming right now: at
        // night they head home / idle at the bunkhouse, panic makes them bolt,
        // and passengers / no-AI golems don't move themselves.
        if (!level().isDay() || getPanic() || isPassenger() || isNoAi()) {
            stuckTicks = 0;
            lastCheckPos = position();
            return;
        }
        if (lastCheckPos == null) {
            lastCheckPos = position();
            return;
        }
        if (position().distanceToSqr(lastCheckPos) > 0.25) { // moved > 0.5 blocks
            stuckTicks = 0;
            lastCheckPos = position();
            return;
        }
        stuckTicks += 40;
        // Hasn't moved half a block in ~90s = stuck, full stop. The old code
        // also required getNavigation().isDone(), but a corrupted golem often
        // clings to a phantom path it can't follow (isDone == false), so that
        // gate skipped the exact golems that needed rebuilding. Motionless is
        // the signal; nav state is not. Nothing legitimate stays this still.
        if (stuckTicks >= STUCK_HEAL_TICKS) {
            org.hero.strawgolem.Constants.LOG.info("Straw Golem {} frozen ~{}s (navDone={}) - self-healing (rebuilding entity).",
                    getId(), STUCK_HEAL_TICKS / 20, getNavigation().isDone());
            selfHeal();
        }
    }

    @SuppressWarnings("unchecked")
    private void selfHeal() {
        if (level().isClientSide) {
            return;
        }
        org.hero.strawgolem.item.GolemRetrainerItem.convert(this,
                (net.minecraft.world.entity.EntityType<? extends StrawGolem>) getType(), level());
    }

    @Override
    protected InteractionResult mobInteract(Player pPlayer, InteractionHand pHand) {
        // If the interaction is on the client side, don't bother (note: the super call is equivalent).
        if (level().isClientSide) return InteractionResult.PASS;
        // Straw Golem's enjoy interaction by a player regardless of the item.
        this.playSound(SoundRegistry.GOLEM_HAPPY.get());
        claimIfUnowned(pPlayer);
        // Get the item the player is holding.
        ItemStack item = pPlayer.getMainHandItem();
        // Currently only doing main hand processing for reduction of bugs/unintended interactions.
        if (pHand == InteractionHand.MAIN_HAND && !item.isEmpty()) {
            // Dye sets the crew colour. Same dye again clears it, so no extra
            // tool is needed to undo one - and unlike Thaumcraft, picking a
            // golem up does NOT wipe it. Losing a crew assignment by tidying up
            // is a papercut with nothing to recommend it.
            if (item.getItem() instanceof net.minecraft.world.item.DyeItem dye) {
                net.minecraft.world.item.DyeColor want = dye.getDyeColor();
                boolean same = want == getCrewColour();
                // Clearing needs SNEAK. It used to be "the same dye again", which
                // made assigning a crew a toggle - and a right-click that lands
                // twice (a stray double-click, or a hand the game retries) then
                // joined and immediately un-joined, leaving the golem undyed and
                // the player reading "left the crew" for a golem they had just
                // hired. A plain dye now only ever ASSIGNS, so repeating it is
                // harmless and idempotent.
                if (same && pPlayer.isShiftKeyDown()) {
                    setCrewColour(null);
                    pPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            displayName() + " left the crew"), true);
                } else if (same) {
                    // Say so rather than silently doing nothing, so a second
                    // click reads as confirmation instead of a dead control.
                    pPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            displayName() + " is already on the " + want.getName()
                                    + " crew (sneak to remove)"), true);
                } else {
                    setCrewColour(want);
                    if (!pPlayer.getAbilities().instabuild) {
                        item.shrink(1);
                    }
                    pPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            displayName() + " joined the " + want.getName() + " crew"), true);
                }
                return InteractionResult.SUCCESS;
            }
            // SNEAK + a seed/crop assigns this golem to that crop (toggle). An
            // empty list means it works everything, which stays the default -
            // so nobody has to set this up unless they want specialists.
            net.minecraft.world.level.block.Block cropBlock =
                    net.minecraft.world.level.block.Block.byItem(item.getItem());
            if (pPlayer.isShiftKeyDown() && cropBlock != net.minecraft.world.level.block.Blocks.AIR) {
                if (harvestFilter.remove(cropBlock)) {
                    pPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            harvestFilter.isEmpty() ? "strawgolem.harvest.cleared" : "strawgolem.harvest.removed",
                            item.getHoverName()), true);
                } else {
                    harvestFilter.add(cropBlock);
                    pPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "strawgolem.harvest.assigned", item.getHoverName()), true);
                }
                refreshAssignmentLabel();
                return InteractionResult.SUCCESS;
            }
            // If the item is a barrel and the Straw Golem is not wearing a fresh barrel.
            if (item.is(Items.BARREL) && barrelHP() != Golem.barrelHealth) {
                // Replace the barrel with the player's held one.
                entityData.set(BARREL, Golem.barrelHealth);
                // Remove a barrel from the player's hand.
                item.shrink(1);
            } else if (Golem.repairItem.contains(item.getItem()) && healthStatus() != 0) {
                // If the player is holding a Straw Golem Repair Item, and the Straw Golem is damaged.

                // Decreasing life span by life a third of max life span to a minimum of 0.
                setLifeSpan(Math.max(0, getLifeSpan() - Golem.maxLife / 3));
                // Updating the Straw Golem's max health
                lifeSpan.refresh();
                // If max health is less than the increase in health, set Straw Golem to max health.
                if (getMaxHealth() - getHealth() < 3.0f) {
                    setHealth(getMaxHealth());
                } else {
                    // Else increase the Straw Golem's health by 3.
                    setHealth(getHealth() + 3.0f);
                }
                // Straw Golems are interested in repairs so play the sound.
                this.playSound(SoundRegistry.GOLEM_INTERESTED.get());
                // Decrement the repair item in the player's hand.
                item.shrink(1);
            } else if (Golem.foodItem.contains(item.getItem()) && getHunger() > Golem.maxHunger / 5) {
                // If the player is holding a Straw Golem Food Item, and the Straw Golem is hungry.

                // Decreasing hunger by a third of max hunger to a minimum of 0.
                setHunger(Math.max(0, getHunger() - Golem.maxHunger / 3));
                // Updating the Straw Golem's hunger.
                hunger.refresh();
                // Straw Golems are interested in food so play the sound.
                this.playSound(SoundRegistry.GOLEM_INTERESTED.get());
                // Decrement the food item in the player's hand.
                item.shrink(1);
            } else if (item.is(ItemRegistry.STRAW_HAT.get()) && !hasHat()) {
                // If the item is a straw hat and the Straw Golem is hatless.

                // Equip the hat.
                entityData.set(HAT, true);
                // Straw Golems are interested in straw hats, so play the sound.
                this.playSound(SoundRegistry.GOLEM_INTERESTED.get());
                // Decrement the hat in the player's hand.
                item.shrink(1);
            } else if (item.is(Items.BRUSH)) {
                // Straw Golems simply enjoy being brushed
                this.playSound(SoundRegistry.GOLEM_HAPPY.get());
                // Clears the winter model.
                entityData.set(FESTIVE, false);
                // Could drain durability here, but doesn't seem notable enough to do so.
            } else {
                // Unrecognized item: don't eat the click. Letting the interaction fall
                // through gives the held item's own entity-interaction a turn (mob
                // capture tools, the golem retrainer, etc.).
                return InteractionResult.PASS;
            }
            // Mark the result as consumption.
            return InteractionResult.CONSUME;
        } else if (pHand == InteractionHand.MAIN_HAND
                    && pPlayer.getMainHandItem().isEmpty() && pPlayer.isCrouching()
                    && pPlayer instanceof GolemOrderer orderer) {
            // If player has nothing in mainhand and is crouching.

            // If a player has no assigned Straw Golem or the assigned is another Straw Golem, select this one.
            if (orderer.strawgolemRewrite$getGolem() == null || !orderer.strawgolemRewrite$getGolem().equals(this)) {
                // Assign the Straw Golem to the player.
                orderer.strawgolemRewrite$setGolem(this);
                // Display an assignment message.
                pPlayer.displayClientMessage(Component.translatable("strawgolem.ordering.start"), true);
            } else {
                // Else unassign the Straw Golem from the player.
                orderer.strawgolemRewrite$setGolem(null);
                // Display an unassignment message.
                pPlayer.displayClientMessage(Component.translatable("strawgolem.ordering.stop"), true);
            }
        }
        return super.mobInteract(pPlayer, pHand);
    }

    // may mess with knockback when barreled, or change this to the hurt method...
    /**
     * Shields a golem from its OWNER's accidental melee. You have to sneak to
     * hit your own golem, the same bargain most pet mods make.
     *
     * <p>Worth it because a straw golem is straw: one careless swing with fire
     * aspect - or a flame charm you forgot you were wearing - deletes a Master
     * rank immortal instantly. Immortality only stops aging, it does nothing
     * about burning, so the most valuable golems are exactly as fragile as the
     * newest ones.
     *
     * <p>Only the owner's direct hits are blocked, and only while not sneaking.
     * Mobs, other players, fire already on the ground, lava and fall damage all
     * still apply - this stops the fat-finger, not the consequences.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide
                && source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && !player.isShiftKeyDown()
                && getOwnerUUID().map(id -> id.equals(player.getUUID())).orElse(false)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("strawgolem.protected"), true);
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource pDamageSource, float pDamageAmount) {
        try {
            // If a snowball and the season is Winter.
            if (pDamageSource.getDirectEntity() instanceof Snowball && isWinter() && !this.level().isClientSide) {
                this.playSound(SoundRegistry.GOLEM_STRAINED.get());
                entityData.set(FESTIVE, true);
                return;
            }
        } catch(Throwable e) {
            LOG.error("Straw Golem: tick threw", e);
        }
        if (barrelHP() - pDamageAmount > 0) { // barrel blocks the damage.
            entityData.set(BARREL, (int) (barrelHP() - pDamageAmount));
            playSound(SoundEvents.SHIELD_BLOCK);
            return;
        } else if (hasBarrel()) { // barrel breaks from the damage.
            // Reduce the damage by the remaining barrel health
            pDamageAmount -= barrelHP();
            entityData.set(BARREL, 0);
            playSound(SoundEvents.SHIELD_BREAK);
        }
        this.playSound(SoundRegistry.GOLEM_HURT.get());
        super.actuallyHurt(pDamageSource, pDamageAmount);
    }


    @Override
    public void die(DamageSource pDamageSource) {
        // Death record. Golems died completely silently before this, which is
        // why "one of my golems is missing" was never answerable after the
        // fact - no log line, no body, nothing to grep.
        if (!level().isClientSide) {
            GolemWatch.reportGone(this, "KILLED", pDamageSource.getMsgId());
            // A short, memory-only grace period. Nothing is written to disk and
            // nothing survives a restart - see Graveyard for why it is kept
            // deliberately narrow.
            Graveyard.remember(this);
            clearFromRoster();
        }
        dropSatchel();   // a loaded satchel used to vanish with the golem
        super.die(pDamageSource);
        // Play Straw Golem death sound upon its death.
        playSound(SoundRegistry.GOLEM_DEATH.get());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CREW_COLOUR, tag.getInt("CrewColour"));
        // Checking if golem speed needs fixed
        // Hat!
        this.entityData.set(HAT, tag.getBoolean("hat"));
        // Absent on golems saved before materials existed - byId falls back to
        // STRAW, which is exactly what an existing golem should be.
        this.entityData.set(MATERIAL,
                GolemMaterial.byId(tag.getString("material")).ordinal());
        applyMaterialStats();
        this.entityData.set(BACKPACK, tag.getBoolean("backpack"));
        this.entityData.set(FESTIVE, tag.getBoolean("festive"));
        // I don't think it's necessary to keep golem panicking?
//        this.entityData.set(PANIC, tag.getBoolean("panic"));
        this.entityData.set(CARRY_STATUS, tag.getInt("carry"));
        // Barrel!
        this.entityData.set(BARREL, tag.getInt("barrelHP"));
        this.entityData.set(HUNGER, tag.getInt("hunger"));
        this.entityData.set(LIFE_SPAN, tag.getInt("lifespan"));
        this.entityData.set(PRIORITY_POS, BlockPos.of(tag.getLong("priorityPos")));
        if (tag.contains("pickupPos")) {
            this.entityData.set(PICKUP_POS, BlockPos.of(tag.getLong("pickupPos")));
        }
        if (tag.contains("homePos")) {
            homePos = BlockPos.of(tag.getLong("homePos"));
        }
        setJobsDone(tag.getInt("jobsDone"));
        entityData.set(IMMORTAL, tag.getBoolean("immortal"));
        if (tag.contains("birthName")) {
            entityData.set(BIRTH_NAME, tag.getString("birthName"));
        }
        if (tag.hasUUID("owner")) {
            setOwnerUUID(tag.getUUID("owner"));
        }
        satchel.clear();
        if (tag.contains("satchel")) {
            net.minecraft.nbt.ListTag stowed = tag.getList("satchel", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < stowed.size(); i++) {
                ItemStack.parse(registryAccess(), stowed.getCompound(i)).ifPresent(satchel::add);
            }
        }
        harvestFilter.clear();
        if (tag.contains("harvestFilter")) {
            net.minecraft.nbt.ListTag filters = tag.getList("harvestFilter", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < filters.size(); i++) {
                net.minecraft.resources.ResourceLocation id =
                        net.minecraft.resources.ResourceLocation.tryParse(filters.getString(i));
                if (id != null) {
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).ifPresent(harvestFilter::add);
                }
            }
        }
        refreshAssignmentLabel();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("CrewColour", entityData.get(CREW_COLOUR));
        // Loading persistent golem data.
        tag.putBoolean("hat", this.hasHat());
        tag.putString("material", getMaterial().id());
        tag.putBoolean("backpack", this.hasBackpack());
        tag.putBoolean("festive", this.entityData.get(FESTIVE));
        tag.putInt("carry", carryStatus());
        tag.putInt("barrelHP", barrelHP());
        tag.putInt("hunger", getHunger());
        tag.putInt("lifespan", getLifeSpan());
        tag.putLong("priorityPos", this.entityData.get(PRIORITY_POS).asLong());
        tag.putLong("pickupPos", this.entityData.get(PICKUP_POS).asLong());
        if (homePos != null) {
            tag.putLong("homePos", homePos.asLong());
        }
        tag.putInt("jobsDone", jobsDone);
        tag.putBoolean("immortal", isImmortal());
        if (!getBirthName().isEmpty()) {
            tag.putString("birthName", getBirthName());
        }
        getOwnerUUID().ifPresent(id -> tag.putUUID("owner", id));
        if (!satchel.isEmpty()) {
            net.minecraft.nbt.ListTag stowed = new net.minecraft.nbt.ListTag();
            for (ItemStack stack : satchel) {
                if (!stack.isEmpty()) {
                    stowed.add(stack.save(registryAccess()));
                }
            }
            tag.put("satchel", stowed);
        }
        if (!harvestFilter.isEmpty()) {
            net.minecraft.nbt.ListTag filters = new net.minecraft.nbt.ListTag();
            for (net.minecraft.world.level.block.Block block : harvestFilter) {
                filters.add(net.minecraft.nbt.StringTag.valueOf(
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()));
            }
            tag.put("harvestFilter", filters);
        }
        super.addAdditionalSaveData(tag);
    }

    /**
     * This method returns an integer depending on the golem's health.
     * @return A status code based on golem health,
     * 0 means essentially full health,
     * 1 means injured,
     * 2 means severely injured.
     */
    public int healthStatus() {
        // basic code to check how dead a golem is.
        // Will return 0 even with minor damage to address lifespan changing health.
        return getHealth() / baseHealth > 0.8 ? 0 : baseHealth * 0.333333 < getHealth() ? 1 : 2;
    }

    /**
     * This method returns an integer depending on the Straw Golem's movement status.
     * @return A status code based on golem movement,
     * 0 means zero movement,
     * 1 means walking,
     * 2 means running.
     */
    public int movementStatus() {
        double movement = getDeltaMovement().horizontalDistance() * level().tickRateManager().tickrate();
        return movement == 0 ? 0 : movement < defaultWalkSpeed * 0.8 ? 1 : 2;
    }

    /**
     * This method returns an integer depending on the Straw Golem's carrying status.
     * @return A status code based on item carrying,
     * 0 means no item,
     * 1 means a regular item,
     * 2 means a block.
     */
    public int carryStatus() {
        return entityData.get(CARRY_STATUS);
    }

    /**
     * Sets the Straw Golem's carry status.
     * @param status The new Straw Golem carry status,
     * 0 means no item,
     * 1 means regular item,
     * 2 means a block.
     */
    public void setCarryStatus(int status) {
        entityData.set(CARRY_STATUS, status);
    }

    /**
     * Determines if the Straw Golem is festive (winter-form).
     * @return Whether the Straw Golem is festive.
     */
    public boolean isFestive() {
        return Golem.winterSkin && entityData.get(FESTIVE);
    }

    /**
     * Determines based on hemisphere if the current season is Winter.
     * @return Whether the current season is Winter.
     */
    private boolean isWinter() {
        Month month = LocalDate.now().getMonth();
        return (Golem.hemisphere.equals("North") && (month == Month.DECEMBER || month == Month.JANUARY))
        || (Golem.hemisphere.equals("South") && (month == Month.JULY || month == Month.AUGUST));
    }

    /**
     * This method returns an integer depending on the golem's pick up status.
     * @return A status code based on if and what the golem is picking up,
     * 0 means not picking up,
     * 1 means picking up an item,
     * 2 means picking up a block.
     */
    public int pickupStatus() {
        return entityData.get(PICKUP_STATUS);
    }

    /**
     * 0 means not picking up, 1 means picking up an item, 2 means picking up a block.
     */
    /**
     * Sets the Straw Golem's pickup status.
     * @param status The new pickup status,
     * 0 means not picking up,
     * 1 means picking up an item,
     * 2 means picking up a block.
     */
    public void setPickupStatus(int status) {
        entityData.set(PICKUP_STATUS, status);
    }

    /**
     * Sets the Straw Golem's pickup status based on the item it is picking up.
     * @param item The item the Straw Golem is picking up.
     */
    public void setPickupStatus(ItemStack item) {
        // If the item is not nothing, continue setting pickup status.
        if (!item.isEmpty()) {
            // If the item is a type of block, set pickup status to 2.
            if (item.getItem() instanceof BlockItem && !(item.getItem() instanceof ItemNameBlockItem)) {
                setPickupStatus(2);
            }
            else {  // Else set pickup status to 1 to indicate a regular item.
                setPickupStatus(1);
            }
        } else {
            // If item is nothing, clear pickup status by setting it to 0.
            setPickupStatus(0);
        }
    }

    /**
     * Checks if the Straw Golem should hold an item above its head.
     * @return Whether the Straw Golem should hold an item above its head.
     */
    public boolean holdItemAbove() {
        // Either the Straw Golem is holding a block,
        // or it is holding an item while wearing a barrel.
        return carryStatus() == 2 || (carryStatus() == 1 && hasBarrel());
    }

    /**
     * Checks if the Straw Golem has a hat.
     * @return Whether the Straw Golem has a hat.
     */
    public boolean hasHat() {
        return entityData.get(HAT);
    }

    /**
     * Gets the health of the Straw Golem's barrel.
     * @return The health of the Straw Golem's barrel.
     */
    public int barrelHP() {
        return entityData.get(BARREL);
    }

    /**
     * Checks if the Straw Golem has a barrel.
     * @return Whether the Straw Golem has a barrel.
     */
    public boolean hasBarrel() {
        // If Barrel Health is not 0, it must have a barrel.
        return barrelHP() != 0;
    }

    /**
     * Sets the Block Position favored by the Straw Golem for delivering items.
     * @param pos The Block Position favored by the Straw Golem for delivering items.
     */
    /** Sets the pickup (supply) chest. Pass null to clear back to "same as drop-off". */
    public void setPickupPos(BlockPos pos) {
        entityData.set(PICKUP_POS, pos == null
                ? new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                : pos.immutable());
    }

    public BlockPos getPickupPos() {
        return entityData.get(PICKUP_POS);
    }

    public boolean hasPickupPos() {
        return getPickupPos().getX() != Integer.MAX_VALUE;
    }

    /**
     * The chest this golem TAKES FROM. Goals that gather ingredients or restock
     * supplies should use this rather than the priority position, so a player
     * can point input and output at different chests - otherwise a crafting
     * golem reads and writes the same box and jams as soon as input outruns it.
     */
    public BlockPos getSupplyPos() {
        return hasPickupPos() ? getPickupPos() : getPriorityPos();
    }

    public void setPriorityPos(BlockPos pos) {
        entityData.set(PRIORITY_POS, pos);
    }

    /**
     * Gets the Block Position favored by the Straw Golem for delivering items.
     * @return The Block Position favored by the Straw Golem for delivering items.
     */
    /**
     * Whether livestock will try to eat this golem. Working golems that must stand
     * among animals (e.g. the breeder) override this to false.
     */
    public boolean isEdibleGolem() {
        return true;
    }

    /**
     * Deposits a stack into the bound priority chest (working golems' "pockets");
     * anything that does not fit is dropped at the golem's feet.
     */
    /** Eats one serving: drops hunger a third and refreshes the speed penalty. */
    public void nourish() {
        setHunger(Math.max(0, getHunger() - Golem.maxHunger / 3));
        hunger.refresh();
    }

    public void depositToChest(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        BlockPos dest = getPriorityPos();
        ItemStack remainder = stack;
        if (!level().isClientSide && dest.getX() != Integer.MAX_VALUE) {
            Container container = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level(), dest);
            if (container != null) {
                remainder = deliverer.insertIntoContainer(container, stack);
            } else {
                remainder = org.hero.strawgolem.platform.Services.PLATFORM.insertItem(level(), dest, stack);
            }
        }
        if (remainder.getCount() < stack.getCount()) {
            recordJob();
        }
        if (!remainder.isEmpty()) {
            spawnAtLocation(remainder);
        }
    }

    /** Remembered bunkhouse position; null until the golem adopts one. */
    private BlockPos homePos = null;

    public BlockPos getHomePos() {
        return homePos;
    }

    public void setHomePos(BlockPos pos) {
        homePos = pos;
    }

    /** Jobs completed over this golem's working life; drives seniority rank. */
    private int jobsDone = 0;
    private static final int JOBS_JOURNEYMAN = 50;
    public static final int JOBS_MASTER = 200;
    private static final net.minecraft.resources.ResourceLocation RANK_SPEED_ID =
            net.minecraft.resources.ResourceLocation.tryBuild(org.hero.strawgolem.Constants.MODID, "rank_speed");

    public int getRank() {
        return entityData.get(RANK);
    }

    /**
     * The crew colour in its stored form: 0 undyed, otherwise DyeColor id + 1.
     *
     * <p>The roster carries this rather than the DyeColor so the value that
     * crosses the network is the same one the golem persists, with no mapping
     * in the middle that could drift.
     */
    public int crewId() {
        net.minecraft.world.item.DyeColor c = getCrewColour();
        return c == null ? 0 : c.getId() + 1;
    }

    /** null when undyed. */
    public net.minecraft.world.item.DyeColor getCrewColour() {
        int v = entityData.get(CREW_COLOUR);
        return v <= 0 ? null : net.minecraft.world.item.DyeColor.byId(v - 1);
    }

    public void setCrewColour(net.minecraft.world.item.DyeColor colour) {
        entityData.set(CREW_COLOUR, colour == null ? 0 : colour.getId() + 1);
    }

    /**
     * Can this golem work that order?
     *
     * <p>An order is either OPEN or locked to a single colour. A blue golem can
     * never touch a red order under any configuration - teams are teams - while
     * an open order is worked by anyone, dyed or not.
     */
    public boolean acceptsOrderColour(net.minecraft.world.item.DyeColor orderColour) {
        return orderColour == null || orderColour == getCrewColour();
    }

    public int getJobsDone() {
        return jobsDone;
    }

    /** Restores seniority (used when converting professions - muscle memory survives). */
    public void setJobsDone(int jobs) {
        jobsDone = jobs;
        entityData.set(RANK, rankFor(jobs));
        applyRankPerks();
    }

    public boolean isImmortal() {
        return entityData.get(IMMORTAL);
    }

    public void setImmortal(boolean value) {
        entityData.set(IMMORTAL, value);
    }

    private static int rankFor(int jobs) {
        return jobs >= JOBS_MASTER ? 2 : jobs >= JOBS_JOURNEYMAN ? 1 : 0;
    }

    /**
     * One completed delivery. Growth requires mortality: immortal golems have
     * stepped outside time and learn nothing new.
     */
    public void recordJob() {
        if (level().isClientSide || isImmortal()) {
            return;
        }
        jobsDone++;
        int rank = rankFor(jobsDone);
        if (rank != getRank()) {
            entityData.set(RANK, rank);
            applyRankPerks();
            playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP);
            if (level() instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        getX(), getY() + 0.5, getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    /** Seniority perk: +10% walk speed per rank, multiplied over everything else. */
    private void applyRankPerks() {
        var attr = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        attr.removeModifier(RANK_SPEED_ID);
        int rank = getRank();
        if (rank > 0) {
            attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    RANK_SPEED_ID, 0.10 * rank,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** The golem's birth name, or "" before one has been rolled. */
    public String getBirthName() {
        return entityData.get(BIRTH_NAME);
    }

    public void setBirthName(String name) {
        entityData.set(BIRTH_NAME, name == null ? "" : name);
    }

    /**
     * What to call this golem anywhere a human reads it.
     *
     * <p>A nametag wins over the birth name, because that is what the player
     * sees floating over its head, in the Clipboard and on a carrier bag. Half
     * the crew here is renamed, so logging the birth name instead means the log
     * and the game disagree about who is who - which is exactly what happened
     * with the work order lines: they named golems the player had never heard
     * of while the same golems sat in his inventory under different labels.
     *
     * <p>Falls back to a dash rather than an empty string so a log line does not
     * silently collapse into nothing for a golem whose name has not rolled yet.
     */
    public String displayName() {
        if (hasCustomName()) {
            return getCustomName().getString();
        }
        return getBirthName().isEmpty() ? "-" : getBirthName();
    }

    /**
     * Rank and immortality are badges on the name: "Mabel Shafto ..".
     *
     * <p>Precedence is name tag, then birth name, then the plain entity type -
     * so tagging a golem still wins, and the fallback keeps working for
     * anything that somehow has no birth name yet.
     */
    @Override
    public net.minecraft.network.chat.Component getName() {
        net.minecraft.network.chat.Component base;
        if (hasCustomName()) {
            base = super.getName();
        } else {
            String birth = getBirthName();
            base = birth.isEmpty()
                    ? super.getName()
                    : net.minecraft.network.chat.Component.literal(birth);
        }
        int rank = getRank();
        boolean timeless = isImmortal();
        if (rank <= 0 && !timeless) {
            return base;
        }
        net.minecraft.network.chat.MutableComponent name = base.copy();
        if (rank > 0) {
            name.append(GolemIcons.rank(rank));
        }
        if (timeless) {
            name.append(GolemIcons.soul());
        }
        return name;
    }

    /** Puts the little straw hat on (or takes it off). Used by profession hats. */
    public void setHat(boolean hasHat) {
        entityData.set(HAT, hasHat);
    }

    public BlockPos getPriorityPos() {
        return entityData.get(PRIORITY_POS);
    }

    /**
     * Whether this golem has a chest bound to it.
     *
     * <p>"No chest" is stored as a priority position with an X of
     * {@link Integer#MAX_VALUE} rather than as null, because the position is
     * synched and {@code BlockPos} has no null on the wire.
     *
     * <p>Seven professions each carried a private copy of this one line. It is
     * not profession-specific in any of them, so it lives here now.
     */
    public boolean hasDepositChest() {
        return getPriorityPos().getX() != Integer.MAX_VALUE;
    }

    /**
     * This determines if the Straw Golem is in rain.
     * @return Whether the Straw Golem is in rain.
     */
    private boolean isInRain() {
        BlockPos blockpos = this.blockPosition();
        return this.level().isRainingAt(blockpos) || this.level().isRainingAt(BlockPos.containing(blockpos.getX(), this.getBoundingBox().maxY, blockpos.getZ()));
    }

    /**
     * This determines if the Straw Golem should be considered cold.
     * @return Whether the Straw Golem is cold.
     */
    private boolean isCold() {
        return !this.level().getBiome(this.blockPosition()).value().warmEnoughToRain(this.blockPosition());
    }

    // May make barrel ignore cold shivering?
    /**
     * This determines if the Straw Golem should shiver.
     * Current determinators: Water/Bubble, Powder Snow, Rain without hat, and Cold Biome.
     * @return Whether the Straw Golem should shiver.
     */
    public boolean shouldShiver() {
        return isInWaterOrBubble() || isInPowderSnow || (!hasHat() && isInRain()) || isCold();
    }

    /**
     * This determines if a Straw Golem needs its animation force reset.
     * @return Whether the Straw Golem needs its animation to be force reset.
     */
    public boolean shouldForceAnimationReset() {
        // Check if the Golem needs its animation to be force reset.
        if (forceAnimationReset) {
            // Now that we know it needs it to be force reset, set it back to false.
            forceAnimationReset = false;
            // Return true to indicate the required animation force reset.
            return true;
        } else {
            // If no force reset needed, return false,
            return false;
        }
    }

    /**
     * This method Straw Golem to need to have its animation force reset.
     */
    public void forceAnimationReset() {
        this.forceAnimationReset = true;
    }

    /**
     * Sets the golem's hunger level.
     * @param hunger The number to set as the golem's hunger.
     */
    public void setHunger(int hunger) {
        entityData.set(HUNGER, hunger);
    }

    /**
     * Gets the golem's hunger level.
     */
    public int getHunger() {
        return entityData.get(HUNGER);
    }

    /**
     * Sets the golem's life span.
     * @param life The number to set as the golem's life.
     */
    public void setLifeSpan(int life) {
        entityData.set(LIFE_SPAN, life);
    }

    /**
     * Gets the golem's life span.
     */
    public int getLifeSpan() {
        return entityData.get(LIFE_SPAN);
    }

    /**
     * Determines how harsh a golem's environment is.
     * This will be used for GolemLifeSpan calculations.
     * @return The harshness value of the environment.
     */
    public float getEnvironmentHarshness() {
        float harsh = 1.0f;
        // If in the water, decay should become more rapid.
        if (isInWaterOrBubble()) {
            harsh++;
        }
        // If in the cold, decay should slow.
        if (isInPowderSnow || isCold()) {
            harsh -= 0.5f;
        }
        // If in the rain without a hat, decay should become more rapid.
        if (isInRain() && !hasHat()) {
            harsh++;
        }
        return harsh;
    }

    /**
     * This method checks if the running goal would make the golem be scared.
     * @return Whether the running goal is a fear causing one.
     */
    private boolean isRunningScaredGoal() {
        for (var goal : this.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof PanicGoal || goal.getGoal() instanceof GolemAvoidEntityGoal<?>) {
                if (goal.isRunning()) {
                    return true;
                }
            }

        }
        return false;
    }

    /**
     * Gets the Straw Golem's panic status.
     * @return The Straw Golem's panic status.
     */
    public boolean getPanic() {
        return Golem.panic && entityData.get(PANIC);
    }

    /**
     * Sets the Straw Golem's panic status
     * @param panic The new panic status.
     */
    public void setPanic(boolean panic) {
        entityData.set(PANIC, panic);
    }

    // ToDo: Move this out of StrawGolem, it can simply be in GolemDepositGoal.
    /**
     * Extra drops from a harvest. A golem has one hand, but a crop can drop
     * several stacks (a second seed, fertilised essence...). Those used to be
     * thrown away entirely - only the first stack was ever taken. The overflow
     * rides along in the golem's satchel and goes into the chest with the rest.
     */
    private final java.util.List<ItemStack> satchel = new java.util.ArrayList<>();
    private static final int SATCHEL_MAX = 12;
    /** Slots a fitted Golem Backpack grants. 36 stacks = a double chest row. */
    public static final int BACKPACK_SLOTS = 36;

    /** Crops this golem will harvest. Empty = harvest anything (default). */
    private final java.util.Set<net.minecraft.world.level.block.Block> harvestFilter = new java.util.LinkedHashSet<>();

    public boolean hasBackpack() {
        return this.entityData.get(BACKPACK);
    }

    public void setBackpack(boolean value) {
        this.entityData.set(BACKPACK, value);
    }

    /**
     * How many satchel slots this golem may use.
     *
     * <p>Without a pack the satchel is OVERFLOW only - it exists so a crop that
     * drops a seed alongside its essence does not throw the seed away. The
     * golem still walks to a chest after every single crop.
     *
     * <p>A Golem Backpack turns that overflow into a cargo hold: the harvest
     * goal stows its primary drop too and keeps picking, so one walk delivers a
     * round instead of one essence. At a chest every 20 blocks that walk was
     * ~95% of the job, which is why the pack is worth roughly six times the
     * throughput.
     */
    /** "unset" for a synched BlockPos. Matches the PRIORITY_POS convention. */
    public static final BlockPos NO_POS =
            new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    public GolemMaterial getMaterial() {
        return GolemMaterial.byOrdinal(this.entityData.get(MATERIAL));
    }

    /**
     * Reforge the body. Keeps everything that makes it THIS golem - name, rank,
     * jobs, soul, home, profession - because the whole point of an upgrade path
     * is that you improve the golem you have rather than replacing it.
     */
    public void setMaterial(GolemMaterial material) {
        this.entityData.set(MATERIAL, material.ordinal());
        applyMaterialStats();
    }

    /**
     * Push the material's numbers onto the attributes.
     *
     * <p>Health is set as a base value and the golem is healed to full, so an
     * upgrade is felt immediately rather than leaving a stone golem sitting at 6
     * of 15 hearts. Speed is a MULTIPLIER on the configured base - it must not
     * stack with the hunger feature, which also writes MOVEMENT_SPEED, so this
     * only ever runs on change and on load.
     */
    public void applyMaterialStats() {
        if (level().isClientSide) {
            return;
        }
        GolemMaterial m = getMaterial();
        var hp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) {
            boolean wasFull = getHealth() >= getMaxHealth() - 0.01F;
            hp.setBaseValue(m.health());
            if (wasFull || getHealth() > m.health()) {
                setHealth(m.health());
            }
        }
        var spd = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(defaultMovement * m.speed());
        }
    }

    /** Jobs completed, readable on the client. */
    public int getJobsSynced() {
        return this.entityData.get(JOBS_SYNC);
    }

    /** Satchel slots in use, readable on the client. */
    public int getBagUsed() {
        return this.entityData.get(BAG_USED);
    }

    /** Home bunkhouse, readable on the client; null when there is none. */
    public BlockPos getHomeSynced() {
        BlockPos p = this.entityData.get(HOME_SYNC);
        return p == null || p.getX() == Integer.MAX_VALUE ? null : p;
    }

    public int satchelCapacity() {
        return hasBackpack() ? BACKPACK_SLOTS : SATCHEL_MAX;
    }

    /** Spill the satchel. Called on death - it used to vanish with the golem. */
    public void dropSatchel() {
        for (ItemStack stack : satchel) {
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        satchel.clear();
    }

    public java.util.List<ItemStack> getSatchel() {
        return satchel;
    }

    /** Stows an extra drop; returns false if the satchel is full. */
    /** Game time the satchel last accepted something. Drives the deposit delay. */
    private long lastStowTick = Long.MIN_VALUE;

    public long lastStowTick() {
        return lastStowTick;
    }

    /** True when the satchel cannot take another distinct stack. */
    public boolean satchelFull() {
        return satchel.size() >= satchelCapacity();
    }

    public boolean stow(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (!level().isClientSide) {
            lastStowTick = level().getGameTime();
        }
        // PARTIAL merges, not all-or-nothing. The old test only merged when the
        // whole incoming stack fitted, so a nearly-full stack of essence forced a
        // brand new slot and the pack filled with part-stacks long before it was
        // actually full.
        ItemStack left = stack.copy();
        for (ItemStack held : satchel) {
            if (left.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(held, left)) {
                int room = held.getMaxStackSize() - held.getCount();
                int move = Math.min(room, left.getCount());
                if (move > 0) {
                    held.grow(move);
                    left.shrink(move);
                }
            }
        }
        if (left.isEmpty()) {
            return true;
        }
        stack = left;
        if (satchel.size() >= satchelCapacity()) {
            return false;
        }
        satchel.add(stack.copy());
        return true;
    }

    public java.util.Set<net.minecraft.world.level.block.Block> getHarvestFilter() {
        return harvestFilter;
    }

    public void replaceHarvestFilter(java.util.Collection<net.minecraft.world.level.block.Block> blocks) {
        harvestFilter.clear();
        harvestFilter.addAll(blocks);
        refreshAssignmentLabel();
    }

    /** What this golem is assigned to, as shown to the player ("" = anything). */
    public java.util.Optional<java.util.UUID> getOwnerUUID() {
        return entityData.get(OWNER);
    }

    public void setOwnerUUID(java.util.UUID id) {
        entityData.set(OWNER, java.util.Optional.ofNullable(id));
    }

    /** First player to handle an unowned golem hires it. */
    public void claimIfUnowned(Player player) {
        if (!level().isClientSide && getOwnerUUID().isEmpty() && player != null) {
            setOwnerUUID(player.getUUID());
        }
    }

    public String getAssignmentLabel() {
        return entityData.get(ASSIGNMENT);
    }

    /**
     * The filter itself is server-side, so publish a readable label for the
     * client - otherwise an assigned golem waiting on its crop just looks like
     * it's slacking, with no way to tell what it's actually waiting for.
     */
    public void refreshAssignmentLabel() {
        if (level().isClientSide) {
            return;
        }
        if (harvestFilter.isEmpty()) {
            entityData.set(ASSIGNMENT, "");
            return;
        }
        StringBuilder label = new StringBuilder();
        for (net.minecraft.world.level.block.Block block : harvestFilter) {
            if (label.length() > 0) {
                label.append(", ");
            }
            label.append(block.getName().getString());
        }
        entityData.set(ASSIGNMENT, label.toString());
    }

    /** No filter set = work every crop, which is the default behaviour. */
    public boolean harvestFilterAccepts(net.minecraft.world.level.block.Block block) {
        if (!harvestFilter.isEmpty() && !harvestFilter.contains(block)) {
            return false;
        }
        // A work order narrows further: the golem's own assignment says what it
        // is willing to work, the order says what the crew has been TOLD to work,
        // and it has to satisfy both. Orders never widen an assignment - a golem
        // trained to wheat does not start cutting carrots because a board says so.
        org.hero.strawgolem.block.WorkOrderBlockEntity order = currentOrder();
        return order == null || order.acceptsBlock(block);
    }

    // --- work orders ------------------------------------------------------
    //
    // Which board this golem is taking instructions from. Resolved on a slow
    // tick and remembered as a POSITION rather than as the block entity: a
    // remembered block entity survives being removed from the world and would
    // quietly keep issuing orders from a board that is no longer there.

    private net.minecraft.core.BlockPos orderPos;
    private int nextOrderScan = 0;

    /** Ticks between re-deciding which order applies. */
    private static final int ORDER_SCAN_INTERVAL = 40;

    /**
     * The order in force for this golem right now, or null.
     *
     * <p>Re-resolved every {@link #ORDER_SCAN_INTERVAL} ticks; between scans the
     * remembered board is re-read (cheap) but not re-chosen (not cheap). That
     * matters because this is called once per candidate crop, and a field sweep
     * looks at a great many candidates.
     */
    public org.hero.strawgolem.block.WorkOrderBlockEntity currentOrder() {
        if (level().isClientSide) {
            return null;
        }
        if (orderPos != null && level().isLoaded(orderPos)
                && level().getBlockEntity(orderPos)
                    instanceof org.hero.strawgolem.block.WorkOrderBlockEntity be
                && be.isActive() && acceptsOrderColour(be.crew())) {
            return be;
        }
        orderPos = null;
        return null;
    }

    /** Slow-tick hook: pick the board this golem answers to. */
    private void tickOrder() {
        if (--nextOrderScan > 0) {
            return;
        }
        nextOrderScan = ORDER_SCAN_INTERVAL;
        org.hero.strawgolem.block.WorkOrderBlockEntity best =
                org.hero.strawgolem.block.WorkOrders.bestFor(this);
        net.minecraft.core.BlockPos found = best == null ? null : best.getBlockPos();
        // Say so when a golem takes an order or loses one. Whether an order is
        // reaching the crew at all is otherwise invisible from in-game: a board
        // that no golem can see looks exactly like a board every golem is
        // ignoring, and the fix for those two is not the same.
        if (!java.util.Objects.equals(found, orderPos)) {
            if (found == null) {
                LOG.info("ORDER dropped | {} '{}' id={} | was {}",
                        getClass().getSimpleName(), displayName(), getId(), orderPos);
            } else {
                LOG.info("ORDER taken | {} '{}' id={} crew={} | board {} crew={} area={} filter={}",
                        getClass().getSimpleName(), displayName(), getId(), getCrewColour(),
                        found, best.crew(), best.hasArea() ? best.volume() + " blocks" : "unset",
                        best.filter().isEmpty() ? "any" : best.filter().size() + " item(s)");
            }
        }
        orderPos = found;
        // Another crew's ground, refreshed on the same slow tick. Resolving
        // boards means block-entity lookups, which must never happen inside a
        // crop search - so it is done once here and the boxes are kept.
        reserved = org.hero.strawgolem.block.WorkOrders.reservedAgainst(this);
    }

    /** Areas belonging to crews this golem is not on. Never null. */
    private java.util.List<net.minecraft.world.phys.AABB> reserved = java.util.List.of();

    /**
     * May this golem work the block at {@code pos}?
     *
     * <p>Two rules, and they are not the same one. If it holds an order it is
     * confined to that order's area. Separately, it may never work inside an
     * area reserved by a crew it is not on - which applies to golems holding no
     * order at all, and is the rule that was missing: an unassigned golem was
     * checked against nothing and would happily harvest another crew's field.
     */
    public boolean mayWorkAt(net.minecraft.core.BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        org.hero.strawgolem.block.WorkOrderBlockEntity order = currentOrder();
        if (order != null && !order.area().contains(x, y, z)) {
            return false;
        }
        for (int i = 0; i < reserved.size(); i++) {
            if (reserved.get(i).contains(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the deposit goal currently knows somewhere to put its goods. The
     * wander goal reads this so a golem holding items it CAN'T deliver is still
     * allowed to move (otherwise it has no runnable goal at all and freezes).
     * A plain flag so wander's canUse stays free - no scanning.
     */
    private boolean depositTargetKnown = true;

    public boolean hasDepositTarget() {
        return depositTargetKnown;
    }

    public void setDepositTargetKnown(boolean known) {
        this.depositTargetKnown = known;
    }

    public class Deliverer {
        BlockPos storagePos;
        BiPredicate<BlockPos> predicate = (gol, pos) ->
                VisionHelper.canSee(gol, pos) && ContainerHelper.isContainer(gol, pos)
                        // Would it actually TAKE what we're carrying? Without this
                        // an energy cube's charge slot or a machine's upgrade slot
                        // counts as storage, and golems keep walking to it.
                        && ContainerHelper.accepts(gol, pos, gol.getMainHandItem())
                        && ReachHelper.canPath(gol, pos)
                        // isBlocked, not isFull: skip containers that refuse what
                        // we're actually holding, so a filtered chest is never
                        // picked as a target and then walked away from.
                        && !org.hero.strawgolem.golem.goals.FullChests.isBlocked(
                                gol.level(), pos, gol.getMainHandItem(), gol.level().getGameTime());

        public boolean shouldChangeDeliverable(BlockPos pos) {
            // This is a XOR, basically:
            // if exactly one of these are true, return true else, return false.
            return pos.equals(getPriorityPos()) ^ predicate.filter(StrawGolem.this, getPriorityPos());
        }

        private long nextScanTime = 0;

        /** Other golems already heading somewhere before we look elsewhere. */
        private static final int CROWD_LIMIT = 2;
        /** How many blocks of extra walking one rival golem is worth avoiding. */
        private static final long CROWD_WEIGHT = 64L;

        public BlockPos getDeliverable() {
            StrawGolem golem = StrawGolem.this;
            long now = golem.level().getGameTime();
            // A chest the player bound by hand always wins - never load-balance
            // away from an explicit order.
            if (getPriorityPos().getX() != Integer.MAX_VALUE && predicate.filter(golem, getPriorityPos())) {
                return getPriorityPos();
            }
            // Stick with the chest we already know (steady, avoids dithering)
            // unless a crowd has formed on it.
            if (storagePos != null && predicate.filter(golem, storagePos)
                    && ChestClaims.others(golem.level(), storagePos, golem.getId(), now) < CROWD_LIMIT) {
                return storagePos;
            }
            // Scanning the whole search cube (with a canPath test per container)
            // is expensive, and this runs every tick while a golem holds goods
            // with nowhere to put them. Throttle the miss case to ~1/sec.
            if (now >= nextScanTime) {
                nextScanTime = now + 20;
                // Weigh crowding against distance so the crew spreads over every
                // available chest instead of all dogpiling the nearest one.
                java.util.Queue<BlockPos> candidates = VisionHelper.nearbyBlocks(golem, predicate);
                BlockPos best = null;
                long bestScore = Long.MAX_VALUE;
                int checked = 0;
                BlockPos candidate;
                while ((candidate = candidates.poll()) != null && checked < 8) {
                    checked++;
                    long score = ChestClaims.others(golem.level(), candidate, golem.getId(), now) * CROWD_WEIGHT
                            + candidate.distManhattan(golem.blockPosition());
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
                if (best != null) {
                    storagePos = best;
                    return best;
                }
            }
            // Nothing in sight: fall back to the chest we REMEMBER, even though
            // it's out of search range. Without this a golem that harvested far
            // from its chest had no deposit target at all - and with a full hand
            // it couldn't harvest or wander either, so it stood frozen until
            // GoHomeGoal dragged it off at nightfall. Now it walks back instead.
            if (storagePos != null && ContainerHelper.isContainer(golem, storagePos)
                    && ContainerHelper.accepts(golem, storagePos, golem.getMainHandItem())
                    && !org.hero.strawgolem.golem.goals.FullChests.isBlocked(
                            golem.level(), storagePos, golem.getMainHandItem(), now)) {
                return storagePos;
            }
            return null;
        }

        /**
         * Puts an arbitrary stack into the container at pos (used to unload the
         * satchel). Returns true once the stack is fully stored.
         */
        public boolean depositStack(LevelReader level, BlockPos pos, ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            Container container = null;
            if (level instanceof net.minecraft.world.level.Level lvl) {
                container = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(lvl, pos);
            } else if (level.getBlockEntity(pos) instanceof Container c) {
                container = c;
            }
            ItemStack remainder = container != null
                    ? insertIntoContainer(container, stack)
                    : org.hero.strawgolem.platform.Services.PLATFORM.insertItem(level, pos, stack);
            stack.setCount(remainder.getCount());
            return remainder.isEmpty();
        }

        /**
         * Why the last {@link #deliver} failed: true = the container refuses this
         * item outright (filtered/locked), false = it was merely out of room.
         * The deposit goal uses this to pick a short or a long cool-off, so a
         * golem stops re-offering goods a container will never accept.
         */
        private boolean lastRejected = false;

        /** True if the last failed deliver() was a refusal, not a full container. */
        public boolean lastFailureWasRejection() {
            return lastRejected;
        }

        /** Whether a vanilla container would take this item in ANY slot, ignoring free space. */
        private boolean accepts(Container container, ItemStack stack) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.canPlaceItem(i, stack)) {
                    return true;
                }
            }
            return false;
        }

        /** Returns true if at least one item was actually deposited (false = chest full / no room). */
        public boolean deliver(LevelReader level, BlockPos pos) {
            lastRejected = false;
            ItemStack item = StrawGolem.this.getMainHandItem();
            if (item.isEmpty()) {
                return false;
            }
            // Resolve a vanilla Container; HopperBlockEntity.getContainerAt merges double chests.
            Container container = null;
            if (level instanceof net.minecraft.world.level.Level lvl) {
                container = net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(lvl, pos);
            } else if (level.getBlockEntity(pos) instanceof Container c) {
                container = c;
            }
            if (container != null) {
                ItemStack after = insertIntoContainer(container, item);
                boolean moved = after.getCount() < item.getCount();
                if (moved) {
                    StrawGolem.this.recordJob();
                } else {
                    // Nothing moved. If no slot would EVER take this item, it's a
                    // refusal (Lunch Cart offered essence, filtered chest), not a
                    // full chest - and re-offering it will never start working.
                    lastRejected = !accepts(container, item);
                }
                StrawGolem.this.setItemSlot(EquipmentSlot.MAINHAND, after);
                refillHandFromCargo();
                return moved;
            }
            // Platform inventories (e.g. NeoForge item handler capability - modded storage).
            ItemStack remainder = org.hero.strawgolem.platform.Services.PLATFORM.insertItem(level, pos, item);
            if (remainder.getCount() != item.getCount()) {
                StrawGolem.this.recordJob();
                StrawGolem.this.setItemSlot(EquipmentSlot.MAINHAND, remainder);
                refillHandFromCargo();
                return true;
            }
            lastRejected = !org.hero.strawgolem.platform.Services.PLATFORM.acceptsItem(level, pos, item);
            return false;
        }

        /**
         * Top the hand up from the backpack once a stack has gone into the chest.
         *
         * <p>The deposit goal runs while the hand is NOT empty, so refilling here
         * makes it drain the whole pack in one visit instead of walking away with
         * fifteen stacks still on its back. No new goal, no new state machine -
         * the existing loop just keeps finding work.
         */
        private void refillHandFromCargo() {
            if (!StrawGolem.this.getMainHandItem().isEmpty()) {
                return;
            }
            java.util.List<ItemStack> bag = StrawGolem.this.getSatchel();
            while (!bag.isEmpty()) {
                ItemStack next = bag.remove(0);
                if (!next.isEmpty()) {
                    StrawGolem.this.setItemSlot(EquipmentSlot.MAINHAND, next);
                    return;
                }
            }
        }

        public ItemStack insertIntoContainer(Container container, ItemStack stack) {
            stack = stack.copy();
            // Respect the container's own placement rules (canPlaceItem) so a
            // filtered container - e.g. the food-only Lunch Cart - never gets
            // stuffed with harvest. If nothing fits, the full stack comes back
            // and the deposit reports failure, so the golem routes elsewhere.
            for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                if (!container.canPlaceItem(i, stack)) {
                    continue;
                }
                ItemStack cItem = container.getItem(i);
                if (!cItem.isEmpty() && ItemStack.isSameItemSameComponents(cItem, stack)) {
                    int limit = Math.min(container.getMaxStackSize(), cItem.getMaxStackSize());
                    int move = Math.min(stack.getCount(), limit - cItem.getCount());
                    if (move > 0) {
                        cItem.grow(move);
                        stack.shrink(move);
                        container.setItem(i, cItem);
                    }
                }
            }
            for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                if (!container.canPlaceItem(i, stack)) {
                    continue;
                }
                if (container.getItem(i).isEmpty()) {
                    int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                    container.setItem(i, stack.split(limit));
                }
            }
            return stack;
        }

    }

}
