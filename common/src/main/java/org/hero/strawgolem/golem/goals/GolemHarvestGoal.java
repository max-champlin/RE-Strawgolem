package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.golem.api.BiPredicate;
import org.hero.strawgolem.golem.api.VisionHelper;

import java.util.Collections;
import java.util.Queue;

public class GolemHarvestGoal extends GolemMoveToBlockGoal {
    private StrawGolem golem;
    private Queue<BlockPos> queue;
    private int harvestTimer = 0;
    private ItemStack item;
    private int approachTicks = 0;
    private boolean gaveUp = false;
    private double lastX;
    private double lastZ;
    private final java.util.Map<BlockPos, Long> ignoreUntil = new java.util.HashMap<>();
    private static final int APPROACH_GIVE_UP = 100;
    private static final int STUCK_NUDGE_AT = 30;
    private static final int IGNORE_TICKS = 200;
    private static final int RESCAN_TICKS = 20; // cap the crop-search sweep to ~1/sec
    private long nextScanTime = 0;
    private BiPredicate<BlockPos> predicate = (gol, pos) -> /*VisionHelper.canSee(gol, pos) && */isGrownPlant(gol.level(), pos) && !isIgnored(pos) && ReachHelper.canPath(gol, pos);
    public GolemHarvestGoal(StrawGolem golem) {
        super(golem, Constants.Golem.defaultWalkSpeed, Constants.Golem.searchRange, Constants.Golem.searchRangeVertical);
        this.golem = golem;
    }

    @Override
    protected boolean isValidTarget(LevelReader levelReader, BlockPos blockPos) {
        return VisionHelper.canSee(golem, blockPos) && predicate.filter(golem, blockPos);
    }

    /** A crop we recently failed to reach; skipped for a while so we move on. */
    private boolean isIgnored(BlockPos pos) {
        Long until = ignoreUntil.get(pos);
        if (until == null) {
            return false;
        }
        if (until <= golem.level().getGameTime()) {
            ignoreUntil.remove(pos);
            return false;
        }
        return true;
    }

    /**
     * Re-path toward the target to break a nav hitch. Deliberately does NOT
     * physically shove the golem - a random shove could push it off a ledge or
     * a floating island into the void (which kills even immortal golems).
     */
    private void unstick() {
        golem.getNavigation().stop();
        moveMobToBlock();
    }

    @Override
    public void tick() {
        try {
            super.tick();
            // Keep our dibs fresh while we work toward / on this crop.
            if (blockPos != null) {
                CropClaims.claim(blockPos, golem.getId(), golem.level().getGameTime());
            }
            // Approach watchdog: while we still can't reach the crop, nudge a
            // wedged golem free, and give up on an unreachable crop entirely so
            // the goal can never lock up waiting on it.
            if (item == null && blockPos != null) {
                if (ReachHelper.canReach(mob, blockPos)) {
                    approachTicks = 0;
                } else {
                    approachTicks++;
                    boolean notMoving = Math.abs(golem.getX() - lastX) < 0.02 && Math.abs(golem.getZ() - lastZ) < 0.02;
                    if (notMoving && approachTicks % STUCK_NUDGE_AT == 0) {
                        unstick();
                    }
                    if (approachTicks > APPROACH_GIVE_UP) {
                        gaveUp = true;
                    }
                }
                lastX = golem.getX();
                lastZ = golem.getZ();
            }
            // Begin harvest phase animation
            if (ReachHelper.canReach(mob, blockPos) && item == null) {
                // harvest time!
                item = harvest();
                golem.setPickupStatus(item);
                golem.getNavigation().stop();
            } else if (item != null && !item.isEmpty()) {
                // Phase Two of harvesting
                golem.getLookControl().setLookAt( blockPos.getCenter().x,  blockPos.getCenter().y,
                        blockPos.getCenter().z, 180, 180);
                // Timer to wait for animation to finish
                harvestTimer++;
                // Another golem harvested block (TODO: Mark blocks with golem IDs, timestamp to avoid this confusion + Smarter harvesting)
                if (harvestTimer == 20 && predicate.filter(golem, blockPos) && ReachHelper.canReach(mob, blockPos)) {
                    // Double checking this, not going to let someone change a block
                    item = harvest();
                    blockReset(golem.level());
                    golem.setItemSlot(EquipmentSlot.MAINHAND, item);
                    // With a pack fitted, stow the primary drop as well and free
                    // the hand so the next crop can be picked immediately. The
                    // satchel already existed for a crop's EXTRA drops; the pack
                    // simply lets the main one ride there too. Without it a golem
                    // walks a full round trip per single essence, and at a chest
                    // every 20 blocks that walk is ~95% of the job.
                    if (golem.hasBackpack() && golem.stow(golem.getMainHandItem())) {
                        golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    }
                } else if (harvestTimer == 40) {
                    golem.setPickupStatus(0);
                } else if (harvestTimer < 20 && !predicate.filter(golem, blockPos)) {
                    golem.forceAnimationReset();
                    golem.setPickupStatus(0);
                    item = null;
                }
            } else if (shouldRecalculatePath() && golemCollision(golem)) {
                // Handling golem collisions.
                nudge(golem);
            }
        } catch (Throwable e) {
            // Using a try-catch to avoid all risk of player crashes.
            Constants.LOG.error(e.getMessage());
            stop();
        }


    }
    @Override
    public void start() {
        if (blockPos == null) blockPos = queue.poll();
        moveMobToBlock();
        this.tryTicks = 0;
        harvestTimer = 0;
        item = null;
        approachTicks = 0;
        gaveUp = false;
        lastX = golem.getX();
        lastZ = golem.getZ();
    }

    @Override
    public void stop() {
        golem.setPickupStatus(0);
        // ToDo: Look into a more gradual stop
        golem.getNavigation().stop();
        if (gaveUp && blockPos != null) {
            ignoreUntil.put(blockPos.immutable(), golem.level().getGameTime() + IGNORE_TICKS);
        }
        CropClaims.release(blockPos, golem.getId());
        gaveUp = false;
        approachTicks = 0;
    }

    @Override
    protected void moveMobToBlock() {
        this.mob.getNavigation().moveTo((double)this.blockPos.getX() + 0.5,
                (double)(this.blockPos.getY()),
                (double)this.blockPos.getZ() + 0.5,
                0, this.speedModifier);
    }

    @Override
    public boolean canUse() {
        long now = golem.level().getGameTime();
        // (Re)build the crop queue only when empty-handed AND out of targets -
        // and never more than once per RESCAN_TICKS. A golem waiting on a
        // still-growing field would otherwise sweep the ENTIRE search cube
        // (running a canPath check per ripe crop) every single tick. Throttling
        // this is the big perf win - the dev's own comment above flagged it.
        if (golem.getMainHandItem().isEmpty() && (queue == null || queue.isEmpty())) {
            if (now < nextScanTime) {
                return false;
            }
            nextScanTime = now + RESCAN_TICKS;
            queue = VisionHelper.nearbyBlocks(golem, predicate);
        }
        // No valid harvest locations or failed to create the queue.
        if (queue == null || queue.isEmpty()) return false;
        do {
            // Skip crops another golem has already called dibs on.
            blockPos = queue.poll();
        } while (blockPos != null
                && (!predicate.filter(golem, blockPos) || CropClaims.isTakenByOther(blockPos, golem.getId(), now))
                && !queue.isEmpty());
        boolean valid = golem.getMainHandItem().isEmpty() && blockPos != null
                && predicate.filter(golem, blockPos)
                && !CropClaims.isTakenByOther(blockPos, golem.getId(), now)
                && isValidTarget(golem.level(), blockPos);
        if (valid) {
            CropClaims.claim(blockPos, golem.getId(), now);
        }
        return valid;
    }

    @Override
    public boolean canContinueToUse() {
        return !gaveUp && harvestTimer <= 38 && (golem.getMainHandItem().isEmpty() ||  harvestTimer <= 40) && isValidTarget(golem.level(), blockPos);
    }

    private boolean isPlant(LevelReader levelReader, BlockPos blockPos) {
        return levelReader != null && blockPos != null && levelReader.getBlockState(blockPos).getBlock() instanceof CropBlock;
    }

    // Checking if the blockpos on the level is a valid and grown plant.
    private boolean isGrownPlant(LevelReader levelReader, BlockPos blockPos) {
        if (levelReader == null || blockPos == null) return false;
        BlockState state = levelReader.getBlockState(blockPos);
        if (Constants.Golem.whitelistHarvest && !Constants.Golem.whitelist.contains(state.getBlock())) {
            return false;
        }
        // Per-golem crop assignment: an empty filter means "work anything",
        // otherwise this golem only touches the crops it was told to.
        if (!golem.harvestFilterAccepts(state.getBlock())) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        } else if (state.getBlock() instanceof BushBlock bush && !(bush instanceof StemBlock)) {
            for (var prop : state.getProperties()) {
                // I wish there was a fruit-bearing bush class...
                if (prop instanceof IntegerProperty intProp && prop.getName().equals("age")
                        && state.getValue(intProp) >= Collections.max(intProp.getPossibleValues()
                )) {
                    return true;
                }
            }
        } else if (Constants.Golem.blockHarvest && state.getBlock()
                == Blocks.PUMPKIN || state.getBlock() == Blocks.MELON) { // Pumpkins and Melons
            // Just going to brute force this for now... don't care about efficiency currently
            try {
                for (Direction dir : Direction.values()) {
                    BlockState stem = levelReader.getBlockState(blockPos.relative(dir));
                    if (stem.getBlock() instanceof AttachedStemBlock) {
                        return stem.hasProperty(AttachedStemBlock.FACING) && blockPos.relative(dir)
                                .relative(stem.getValue(AttachedStemBlock.FACING))
                                .equals(blockPos);
                    }
                }
            } catch (Throwable e) {
                Constants.LOG.error(e.getMessage());
                return false;
            }
        }
        return false;
    }

    // Gets the item as if harvested from the plant.
    public ItemStack harvest() {
        if (golem.level() instanceof ServerLevel level) {
            BlockState state = level.getBlockState(blockPos);
            LootParams.Builder builder = new LootParams.Builder(level).
                    withParameter(LootContextParams.TOOL, ItemStack.EMPTY).
                    withParameter(LootContextParams.ORIGIN, mob.position());
            // Take EVERYTHING the crop drops, not just the first stack. A crop
            // can roll a second seed and fertilised essence on top of its
            // essence; those used to be discarded outright (the block is reset
            // rather than broken, so they were never even dropped on the floor).
            // The best stack goes in hand, the rest ride in the satchel.
            java.util.List<ItemStack> all = new java.util.ArrayList<>(state.getDrops(builder));
            if (all.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int best = 0;
            for (int i = 0; i < all.size(); i++) {
                if (isCropDrop(all.get(i))) {
                    best = i;
                    break;
                }
            }
            ItemStack primary = all.remove(best);
            for (ItemStack extra : all) {
                if (!golem.stow(extra)) {
                    // Satchel full - drop it at our feet rather than delete it.
                    net.minecraft.world.level.block.Block.popResource(level, blockPos, extra);
                }
            }
            return primary;
        } else {
            Constants.LOG.error("Golem level not ServerLevel!");
            return ItemStack.EMPTY;
        }
    }

    // Resets the age of the crop.
    private void blockReset(Level level) {
        BlockState state = level.getBlockState(blockPos);
        boolean hasAge = false;
        for (Property<?> prop : state.getProperties()) {
            // Let's assume age is not a weird property...
            if (prop.getName().equalsIgnoreCase("age") && prop instanceof IntegerProperty intprop) {
                hasAge = true;
                int value = state.getBlock().defaultBlockState().getValue(intprop);
                level.playSound(null, blockPos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS);
                state = state.setValue(intprop, value);
                level.setBlockAndUpdate(blockPos, state);
                break;
            }
        }
        if (!hasAge) {
            try {
                for (Direction dir : Direction.values()) {
                    BlockState stem = golem.level().getBlockState(blockPos.relative(dir));
                    if (stem.getBlock() instanceof AttachedStemBlock) {
                        if (stem.hasProperty(AttachedStemBlock.FACING) && blockPos.relative(dir)
                                .relative(stem.getValue(AttachedStemBlock.FACING))
                                .equals(blockPos)) {
                            golem.level().setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            } catch (Throwable e) {
                Constants.LOG.error(e.getMessage());
                // I'd rather wipe the block rather than duplicate it.
                golem.level().setBlockAndUpdate(blockPos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    // Checking if an ItemStack is a crop drop and not seeds.
    private boolean isCropDrop(ItemStack item) {
        return !(item.getItem() instanceof ItemNameBlockItem)
                || item.getItem().components().has(DataComponents.FOOD);
    }

}
