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

    /**
     * The Golem Apartment: three bunkhouses in one block, sleeping up to 36.
     *
     * <p>A bunkhouse caps at twelve, so a farm needing twenty-six golems needs
     * three buildings and twelve hay bales. This is the same dormitory scaled
     * up rather than repeated.
     */
    /**
     * Nine ghast tears, pressed into a block. Vanilla has no such thing and
     * nothing in the pack adds one, so it is here purely as a material for
     * Baba Yaga's Promise - and because a wall of them is a fair way to show
     * what a resurrection cost you.
     */
    public static final Supplier<Block> GHAST_TEAR_BLOCK = Constants.COMMON_PLATFORM.registerBlock("ghast_tear_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(1.2F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 4)));

    public static final Supplier<Block> GOLEM_APARTMENT = Constants.COMMON_PLATFORM.registerBlock("golem_apartment",
            () -> new org.hero.strawgolem.block.ApartmentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.8F)
                    .sound(SoundType.GRASS)));

    /**
     * A work order board: says WHERE the work is, how urgent, which crew and
     * what to act on. Never says what a golem can DO - that is its class.
     */
    public static final Supplier<Block> WORK_ORDER = Constants.COMMON_PLATFORM.registerBlock("work_order",
            () -> new org.hero.strawgolem.block.WorkOrderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

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

    public static final Supplier<BlockEntityType<org.hero.strawgolem.block.ApartmentBlockEntity>> APARTMENT_BLOCK_ENTITY =
            Constants.COMMON_PLATFORM.registerBlockEntity("golem_apartment",
                    org.hero.strawgolem.block.ApartmentBlockEntity::new, GOLEM_APARTMENT);

    public static final Supplier<BlockEntityType<org.hero.strawgolem.block.WorkOrderBlockEntity>> WORK_ORDER_BLOCK_ENTITY =
            Constants.COMMON_PLATFORM.registerBlockEntity("work_order",
                    org.hero.strawgolem.block.WorkOrderBlockEntity::new, WORK_ORDER);
}
