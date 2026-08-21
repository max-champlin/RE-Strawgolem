package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.golem.api.VisionHelper;

public class GolemDepositGoal extends GolemMoveToBlockGoal {

    /**
     * Ticks with nothing new stowed before a part-full pack is delivered.
     *
     * <p>Three seconds. Long enough that walking between two crops never counts
     * as "finished", short enough that a golem which has cleared its patch does
     * not stand about holding the harvest.
     */
    private static final int IDLE_BEFORE_DELIVER = 60;

    private static final int STUCK_NUDGE_AT = 30;
    private static final int APPROACH_GIVE_UP = 100; // ~5s unable to reach -> bail
    private StrawGolem golem;
    private boolean done = false;
    private int approachTicks = 0;
    private double lastDist = Double.MAX_VALUE;
    public GolemDepositGoal(StrawGolem golem) {
        super(golem, Golem.defaultWalkSpeed, Golem.searchRange, Golem.searchRangeVertical);
        this.golem = golem;
    }

    @Override
    public void start() {
        // Fresh attempt. Without this reset `done` stayed true after the very
        // first delivery, so canContinueToUse was false forever and the goal
        // restarted EVERY TICK - re-issuing a path request each time and wiping
        // the approach watchdog before it could ever count up.
        done = false;
        // Safety check in case getDeliverable fails
        if (blockPos == null) {
            Constants.LOG.error("Deposit Start Error!");
            stop();
            done = true;
        } else {
            moveMobToBlock();
            this.tryTicks = 0;
            approachTicks = 0;
            lastDist = Double.MAX_VALUE;
        }
    }

    @Override
    public void tick() {
        // Hopefully unneeded try-catch, but I want players to never have an unwanted crash.
        try {
            // This should never have been possible, but it is happening.
            if (this.blockPos == null) {
                Constants.LOG.error("Missing block position!");
                return;
            }
            super.tick();
            if (mob.hasItemInSlot(EquipmentSlot.MAINHAND) && ReachHelper.canReach(mob, blockPos)) {
                approachTicks = 0;
                if (golem.deliverer.deliver(golem.level(), blockPos)) {
                    // Empty the satchel (extra harvest drops) into the same chest.
                    java.util.List<net.minecraft.world.item.ItemStack> satchel = golem.getSatchel();
                    satchel.removeIf(stack -> stack.isEmpty()
                            || golem.deliverer.depositStack(golem.level(), blockPos, stack));
                    tryPlaySound();
                    golem.getNavigation().stop();
                    done = true;
                } else {
                    // Deposit failed - but WHY matters. A full chest frees up when
                    // you unload it, so a short flag is right. A container that
                    // refuses this item (food-only cart, filtered chest) never
                    // will, and a short flag just sends the golem back every ten
                    // seconds to be refused again - offer, refuse, turn around,
                    // repeat. Rejections get a much longer cool-off, per item.
                    long now = golem.level().getGameTime();
                    if (golem.deliverer.lastFailureWasRejection()) {
                        FullChests.markRejected(blockPos, golem.getMainHandItem(), now);
                    } else {
                        FullChests.markFull(blockPos, now);
                    }
                    golem.getNavigation().stop();
                    done = true;
                }
            } else if (mob.hasItemInSlot(EquipmentSlot.MAINHAND)) {
                // APPROACH WATCHDOG. The target may be a REMEMBERED chest well
                // outside pathfinding range (a golem that harvested across a big
                // field), so progress is measured by distance closing - not by a
                // flat timer, which would abandon a legitimate long walk home.
                double dist = Math.sqrt(blockPos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()));
                if (dist < lastDist - 0.05) {
                    approachTicks = 0; // still closing on the chest
                    lastDist = dist;
                } else {
                    approachTicks++;
                }
                // MoveToBlockGoal re-paths straight at the chest every 40 ticks;
                // for a far target that path fails and leaves navigation idle, so
                // re-issue our own (hopped) move whenever nav goes quiet.
                if (golem.getNavigation().isDone() || (approachTicks > 0 && approachTicks % STUCK_NUDGE_AT == 0)) {
                    golem.getNavigation().stop();
                    moveMobToBlock();
                }
                if (approachTicks > APPROACH_GIVE_UP) {
                    // Genuinely not getting closer - skip this chest for a bit so
                    // canUse picks another (or none, and Wander takes over).
                    FullChests.markFull(blockPos, golem.level().getGameTime());
                    golem.getNavigation().stop();
                    done = true;
                }
                if (shouldRecalculatePath() && golemCollision(golem)) {
                    nudge(golem);
                }
            }
        } catch (Throwable e) {
            Constants.LOG.error("Golem Deposit Goal has experienced an error: {}", e);
        }
    }

    @Override
    public boolean canUse() {
        if (blockPos == null || !ContainerHelper.isContainer(golem, blockPos)
                || !ContainerHelper.accepts(golem, blockPos, golem.getMainHandItem())
                || !VisionHelper.canSee(golem, blockPos)
                || FullChests.isBlocked(blockPos, golem.getMainHandItem(), golem.level().getGameTime())
                || golem.deliverer.shouldChangeDeliverable(blockPos)) {
            blockPos = golem.deliverer.getDeliverable();
        }
        // Tell the wander goal whether we have anywhere to put things. With a
        // full hand and no target, wander must be allowed to run or the golem
        // has no runnable goal at all and freezes on the spot.
        golem.setDepositTargetKnown(blockPos != null);
        if (blockPos != null) {
            // Tell the other golems this chest is spoken for, so they spread out
            // instead of all queuing on the same one.
            ChestClaims.claim(blockPos, golem.getId(), golem.level().getGameTime());
        }
        // A loaded backpack counts as having something to deliver - but ONLY
        // once gathering has actually finished.
        //
        // This fought the harvest goal head-on: harvest stows the crop to free
        // the hand so it can pick the next one, and this pulled it straight back
        // out and set off for the chest. One trip per crop, exactly as before,
        // with the pack adding a step and nothing else. The pack looked fitted
        // and did nothing.
        //
        // So wait for one of two things: the pack is FULL, or nothing has gone
        // into it for a few seconds, which means the golem has run out of ripe
        // crops in reach. That still covers the case this was written for - a
        // part-filled pack with no work left never strands the golem - without
        // interrupting a round that is still going.
        boolean gatheringDone = golem.satchelFull()
                || golem.level().getGameTime() - golem.lastStowTick() > IDLE_BEFORE_DELIVER;
        if (gatheringDone
                && !mob.hasItemInSlot(EquipmentSlot.MAINHAND) && !golem.getSatchel().isEmpty()) {
            java.util.List<ItemStack> bag = golem.getSatchel();
            while (!bag.isEmpty()) {
                ItemStack next = bag.remove(0);
                if (!next.isEmpty()) {
                    golem.setItemSlot(EquipmentSlot.MAINHAND, next);
                    break;
                }
            }
        }
        return mob.hasItemInSlot(EquipmentSlot.MAINHAND)
                && blockPos != null;
    }

    @Override
    public void stop() {
        // Let the other golems know this chest is free again.
        ChestClaims.release(blockPos, golem.getId());
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        return !done && ContainerHelper.isContainer(mob, blockPos)
                && canUse() && golem.getNavigation().getPath() != null;
    }

    /** How far we path in one go when walking back to a distant chest. */
    private static final double HOP = 12.0;

    @Override
    protected void moveMobToBlock() {
        try {
            // A remembered chest can sit beyond FOLLOW_RANGE, where a direct path
            // request just fails and the golem stands still. Walk it in hops:
            // aim at a point along the way, and repeat until the chest is close
            // enough to path to properly.
            double dx = this.blockPos.getX() + 0.5 - this.mob.getX();
            double dz = this.blockPos.getZ() + 0.5 - this.mob.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat > HOP) {
                double t = HOP / flat;
                this.mob.getNavigation().moveTo(this.mob.getX() + dx * t, this.mob.getY(),
                        this.mob.getZ() + dz * t, this.speedModifier);
                return;
            }
            this.mob.getNavigation().moveTo((double) this.blockPos.getX() + 0.5, (double) (this.blockPos.getY()), (double) this.blockPos.getZ() + 0.5, 0, this.speedModifier);
        } catch(Exception e) {
            stop();
            Constants.LOG.error(e.getMessage());
        }

    }

    private SoundEvent tryParseSound() {
        try {
            BlockEntity block = golem.level().getBlockEntity(blockPos);
            if (block == null) return null;

            ResourceLocation location = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(block.getType());
            if (location == null) return null;
            return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse("block." + location.getPath() + ".open"));
        } catch (Throwable e) {
            return null;
        }
    }

    private void tryPlaySound() {
        SoundEvent event = tryParseSound();
        if (event == null) {
            event = SoundEvents.CHEST_OPEN;
        }
        golem.level().playSound(null, blockPos, event,
                SoundSource.BLOCKS, 0.5F, golem.level().random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
        // Not sure which way is more efficient, or if the order even matters...
        return ContainerHelper.isContainer(levelReader, blockPos) && VisionHelper.canSee(mob, mob.getOnPos());
    }

}
