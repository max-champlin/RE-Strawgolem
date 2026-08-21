package org.hero.strawgolem.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.block.BunkhouseBlock;
import org.hero.strawgolem.block.BunkhouseBlockEntity;

import java.util.function.Supplier;

public final class BlockRegistry {
    public static void init() {}

    /**
     * The Golem Bunkhouse: at night or in rain, idle golems within range head
     * for the nearest bunkhouse and check in like bees into a hive.
     */
    public static final Supplier<Block> GOLEM_BUNKHOUSE = Constants.COMMON_PLATFORM.registerBlock("golem_bunkhouse",
            () -> new BunkhouseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.6F)
                    .sound(SoundType.GRASS)));

    /** Umbrella stand for Foreman's Sticks: holds six, names intact. */
    public static final Supplier<Block> STICK_STAND = Constants.COMMON_PLATFORM.registerBlock("stick_stand",
            () -> new org.hero.strawgolem.block.StickStandBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    /** Shared pantry golems eat from when hunger is enabled. */
    public static final Supplier<Block> LUNCH_CART = Constants.COMMON_PLATFORM.registerBlock("lunch_cart",
            () -> new org.hero.strawgolem.block.LunchCartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.6F)
                    .sound(SoundType.WOOD)));

    /** Wall-mounted Foreman's Clipboard - the crew roster by the door. */
    public static final Supplier<Block> DIRECTORY_BOARD = Constants.COMMON_PLATFORM.registerBlock("directory_board",
            () -> new org.hero.strawgolem.block.DirectoryBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.4F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final Supplier<BlockEntityType<org.hero.strawgolem.block.LunchCartBlockEntity>> LUNCH_CART_BLOCK_ENTITY =
            Constants.COMMON_PLATFORM.registerBlockEntity("lunch_cart",
                    org.hero.strawgolem.block.LunchCartBlockEntity::new, LUNCH_CART);

    public static final Supplier<BlockEntityType<org.hero.strawgolem.block.StickStandBlockEntity>> STICK_STAND_BLOCK_ENTITY =
            Constants.COMMON_PLATFORM.registerBlockEntity("stick_stand",
                    org.hero.strawgolem.block.StickStandBlockEntity::new, STICK_STAND);

    public static final Supplier<BlockEntityType<BunkhouseBlockEntity>> BUNKHOUSE_BLOCK_ENTITY =
            Constants.COMMON_PLATFORM.registerBlockEntity("golem_bunkhouse",
                    BunkhouseBlockEntity::new, GOLEM_BUNKHOUSE);
}
