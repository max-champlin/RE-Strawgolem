package org.hero.strawgolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.hero.strawgolem.registry.BlockRegistry;
import org.hero.strawgolem.registry.ItemRegistry;

/**
 * Worker housing. Right-click for a status readout; sneak-right-click toggles
 * auto-retire; right-click with an Immortal Soul to bank one. Breaking the
 * house evicts the tenants and returns any banked souls.
 */
public class BunkhouseBlock extends Block implements EntityBlock {

    /**
     * Is this position somewhere a golem can actually check in?
     *
     * <p>Deliberately NOT an exact block match. The apartment is a different
     * block that extends this one, and matching {@code GOLEM_BUNKHOUSE} exactly
     * made golems blind to it - they walked past a thirty-six bunk building to
     * queue outside a twelve.
     *
     * <p>Requiring the block ENTITY as well handles the apartment's upper two
     * storeys for free: they are shell blocks with no entity, so a golem never
     * tries to sleep in the roof.
     */
    public static boolean isHousing(net.minecraft.world.level.BlockGetter level,
                                    net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BunkhouseBlock
                && level.getBlockEntity(pos) instanceof BunkhouseBlockEntity;
    }

    public BunkhouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BunkhouseBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BlockRegistry.BUNKHOUSE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<BunkhouseBlockEntity>) BunkhouseBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() != ItemRegistry.IMMORTAL_SOUL.get()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof BunkhouseBlockEntity house) {
            // Sneak with a fistful of souls to bring back the most recent loss
            // homed here. A plain click still banks one for retirement, which
            // is the cheaper and better answer - this is for when you were too
            // late.
            if (player.isShiftKeyDown()) {
                var pending = org.hero.strawgolem.golem.Graveyard.pending(pos);
                if (pending.isEmpty()) {
                    player.displayClientMessage(Component.translatable(
                            "strawgolem.bunkhouse.nograves"), true);
                    return ItemInteractionResult.sidedSuccess(false);
                }
                int cost = org.hero.strawgolem.golem.Graveyard.SOUL_COST;
                if (!player.getAbilities().instabuild && stack.getCount() < cost) {
                    player.displayClientMessage(Component.translatable(
                            "strawgolem.bunkhouse.needsouls", cost), true);
                    return ItemInteractionResult.sidedSuccess(false);
                }
                BlockPos out = pos.above();
                String who = level instanceof net.minecraft.server.level.ServerLevel sl
                        ? org.hero.strawgolem.golem.Graveyard.raise(sl, pos, out) : null;
                if (who == null) {
                    return ItemInteractionResult.sidedSuccess(false);
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(cost);
                }
                level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
                player.displayClientMessage(Component.translatable(
                        "strawgolem.bunkhouse.raised", who), false);
                return ItemInteractionResult.sidedSuccess(false);
            }
            house.bankSoul();
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.3F);
            player.displayClientMessage(Component.translatable("strawgolem.bunkhouse.soulbanked", house.bankedSouls()), true);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BunkhouseBlockEntity house) {
            if (player.isShiftKeyDown()) {
                boolean on = house.toggleAutoRetire();
                player.displayClientMessage(Component.translatable(
                        on ? "strawgolem.bunkhouse.autoretire_on" : "strawgolem.bunkhouse.autoretire_off"), true);
            } else {
                player.displayClientMessage(Component.translatable("strawgolem.bunkhouse.status",
                        house.occupants(), house.capacity(), house.bankedSouls(),
                        Component.translatable(house.isAutoRetire() ? "strawgolem.on" : "strawgolem.off")), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BunkhouseBlockEntity house) {
            house.releaseAll();
            house.dropBankedSouls();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
