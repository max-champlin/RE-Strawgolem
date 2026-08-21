package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ReachHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Sprinkles bonemeal on anything nearby that grows: crops, saplings, berry
 * bushes, flowers. Grass blocks and other replaceables are excluded so he
 * fertilizes the garden, not the lawn.
 */
public class GardenerFeedGoal extends Goal {
    private static final int SPRINKLE_TICKS = 10;
    private static final int SCAN_COOLDOWN = 40;

    private final StrawGolem golem;
    private BlockPos target;
    private int sprinkleTicks;
    private int scanCooldown;

    public GardenerFeedGoal(StrawGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Nearest block that would actually benefit from bonemeal. */
    public static BlockPos findTarget(StrawGolem golem) {
        BlockPos center = golem.blockPosition();
        int r = Golem.searchRange;
        int rv = Golem.searchRangeVertical;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -rv, -r), center.offset(r, rv, r))) {
            BlockState state = golem.level().getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bm
                    && !state.is(Blocks.GRASS_BLOCK)
                    && !state.canBeReplaced()
                    && bm.isValidBonemealTarget(golem.level(), pos, state)
                    && ReachHelper.canPath(golem, pos)) {
                candidates.add(pos.immutable());
            }
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(p -> p.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ())))
                .orElse(null);
    }

    @Override
    public boolean canUse() {
        if (!golem.getMainHandItem().is(Items.BONE_MEAL)) {
            return false;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_COOLDOWN;
        target = findTarget(golem);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && golem.getMainHandItem().is(Items.BONE_MEAL);
    }

    @Override
    public void start() {
        sprinkleTicks = 0;
        moveToTarget();
    }

    @Override
    public void stop() {
        target = null;
        sprinkleTicks = 0;
        golem.getNavigation().stop();
    }

    private void moveToTarget() {
        golem.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        golem.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (target.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > 6.25) {
            if (golem.getNavigation().isDone()) {
                moveToTarget();
            }
            sprinkleTicks = 0;
            return;
        }
        sprinkleTicks++;
        if (sprinkleTicks < SPRINKLE_TICKS) {
            return;
        }
        if (golem.level() instanceof ServerLevel level) {
            BlockState state = level.getBlockState(target);
            if (state.getBlock() instanceof BonemealableBlock bm
                    && bm.isValidBonemealTarget(level, target, state)) {
                if (bm.isBonemealSuccess(level, level.random, target, state)) {
                    bm.performBonemeal(level, level.random, target, state);
                }
                level.levelEvent(1505, target, 15);
                ItemStack hand = golem.getMainHandItem();
                hand.shrink(1);
            }
        }
        target = null;
    }
}
