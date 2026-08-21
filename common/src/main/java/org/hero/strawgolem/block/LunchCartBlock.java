package org.hero.strawgolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A stockable pantry golems eat from. Right-click with food to load it,
 * empty hand for a serving count. Hoppers can feed it automatically - pipe a
 * Cook golem's output in and the mess hall fills itself.
 */
public class LunchCartBlock extends Block implements EntityBlock {

    public LunchCartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LunchCartBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!LunchCartBlockEntity.isFood(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (level.getBlockEntity(pos) instanceof LunchCartBlockEntity cart) {
            ItemStack leftover = cart.stock(player.getAbilities().instabuild ? stack.copy() : stack);
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, leftover);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("strawgolem.lunchcart.stocked", cart.servings()), true);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Empty hand opens the cart like a chest so food can be taken back out.
        if (level.getBlockEntity(pos) instanceof LunchCartBlockEntity cart) {
            player.openMenu(cart);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LunchCartBlockEntity cart) {
            Containers.dropContents(level, pos, cart);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
