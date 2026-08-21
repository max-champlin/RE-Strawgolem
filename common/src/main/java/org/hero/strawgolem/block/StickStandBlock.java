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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.hero.strawgolem.item.GolemRetrainerItem;

/**
 * Umbrella stand for Foreman's Sticks. Click with a stick to store it, empty
 * hand to take the top one back, sneak with an empty hand for the inventory
 * list. Holds six.
 */
public class StickStandBlock extends Block implements EntityBlock {
    public static final BooleanProperty HAS_STICKS = BooleanProperty.create("has_sticks");
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 12.0, 12.0);

    public StickStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HAS_STICKS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_STICKS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StickStandBlockEntity(pos, state);
    }

    private static void refreshFill(Level level, BlockPos pos, BlockState state, StickStandBlockEntity stand) {
        level.setBlock(pos, state.setValue(HAS_STICKS, stand.count() > 0), 3);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof GolemRetrainerItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof StickStandBlockEntity stand) {
            if (stand.count() >= StickStandBlockEntity.CAPACITY) {
                player.displayClientMessage(Component.translatable("strawgolem.stickstand.full"), true);
                return ItemInteractionResult.sidedSuccess(false);
            }
            ItemStack single = stack.split(1);
            Component name = single.getHoverName();
            stand.insert(single);
            refreshFill(level, pos, state, stand);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("strawgolem.stickstand.stored",
                    name, stand.count(), StickStandBlockEntity.CAPACITY), true);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof StickStandBlockEntity stand) {
            if (stand.count() == 0) {
                player.displayClientMessage(Component.translatable("strawgolem.stickstand.empty"), true);
                return InteractionResult.CONSUME;
            }
            if (player.isShiftKeyDown()) {
                Component names = null;
                for (ItemStack stick : stand.view()) {
                    names = names == null ? stick.getHoverName().copy()
                            : ((net.minecraft.network.chat.MutableComponent) names).append(", ").append(stick.getHoverName());
                }
                player.displayClientMessage(Component.translatable("strawgolem.stickstand.list", names), true);
                return InteractionResult.CONSUME;
            }
            ItemStack stick = stand.takeLast();
            refreshFill(level, pos, state, stand);
            Component name = stick.getHoverName();
            if (!player.getInventory().add(stick)) {
                player.drop(stick, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("strawgolem.stickstand.took", name), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StickStandBlockEntity stand) {
            for (ItemStack stick : stand.view()) {
                Block.popResource(level, pos, stick);
            }
            stand.view().clear();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
