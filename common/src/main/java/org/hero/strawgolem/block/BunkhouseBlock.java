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
