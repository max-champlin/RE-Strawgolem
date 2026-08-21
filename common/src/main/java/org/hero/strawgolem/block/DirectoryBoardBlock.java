package org.hero.strawgolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Employee Board: a wall-mounted Foreman's Clipboard.
 *
 * <p>Right-click it to open the same roster the clipboard shows, so a base can
 * have the crew list hanging by the door instead of one player carrying the
 * only copy. Deliberately has NO block entity - the screen reads golem state
 * straight off the entities the client already tracks, so the board is purely
 * a place to click.
 */
public class DirectoryBoardBlock extends HorizontalDirectionalBlock {
    public static final com.mojang.serialization.MapCodec<DirectoryBoardBlock> CODEC =
            simpleCodec(DirectoryBoardBlock::new);

    /** Two pixels proud of the wall, like a hanging notice board. */
    private static final VoxelShape NORTH = Block.box(1.0, 1.0, 14.0, 15.0, 15.0, 16.0);
    private static final VoxelShape SOUTH = Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 2.0);
    private static final VoxelShape WEST = Block.box(14.0, 1.0, 1.0, 16.0, 15.0, 15.0);
    private static final VoxelShape EAST = Block.box(0.0, 1.0, 1.0, 2.0, 15.0, 15.0);

    public DirectoryBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Faces out from whatever it was placed against. Clicking a wall gives the
     * hanging-board result; clicking the floor or ceiling falls back to facing
     * the player, so it can't be placed into an un-clickable orientation.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal()
                ? face
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (level.isClientSide) {
            openDirectory();
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Split out so the screen class is only touched on a client branch - keeps
     * a dedicated server from ever loading client-only classes.
     */
    private static void openDirectory() {
        org.hero.strawgolem.client.GolemDirectoryScreen.open();
    }
}
