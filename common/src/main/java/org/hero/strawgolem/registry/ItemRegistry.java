package org.hero.strawgolem.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.platform.Services;
import org.hero.strawgolem.registry.EntityRegistry;

import java.util.function.Supplier;

// ToDo:
// Add Straw Hat (and functionality)
// Possible routes:
// Make other hats for other "roles"
// ^^ Addition: Remove default golem farming, and require straw hat to farm
public class ItemRegistry {
    public static void init() {
        Services.PLATFORM.registerCreativeModeTab("strawgolem_tab", () ->
                Services.PLATFORM.newCreativeTabBuilder()
                        .title(Component.translatable("itemGroup.strawgolem.tab"))
                        .icon(() -> new ItemStack(ItemRegistry.STRAW_HAT.get()))
                        .displayItems((parameters, output) -> {
                                output.accept(ItemRegistry.STRAW_HAT.get());
                                output.accept(ItemRegistry.BREEDER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.STOCK_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.MINER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.BEEKEEPER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.FISHER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.LUMBERJACK_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.SMELTER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.EXCAVATOR_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.COOK_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.JANITOR_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.MILKMAID_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.GARDENER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.BREWER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.BUTCHER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.ARTISAN_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.METALWORKER_GOLEM_SPAWN_EGG.get());
                                output.accept(ItemRegistry.GOLEM_RETRAINER.get());
                                output.accept(ItemRegistry.GOLEM_CARRIER.get());
                                output.accept(ItemRegistry.GOLEM_BACKPACK.get());
                                output.accept(ItemRegistry.GOLEM_DIRECTORY.get());
                                output.accept(ItemRegistry.TRAFFIC_CONE.get());
                                output.accept(ItemRegistry.BREEDER_BADGE.get());
                                output.accept(ItemRegistry.STOCK_BADGE.get());
                                output.accept(ItemRegistry.MINER_BADGE.get());
                                output.accept(ItemRegistry.EXCAVATOR_BADGE.get());
                                output.accept(ItemRegistry.BEEKEEPER_BADGE.get());
                                output.accept(ItemRegistry.FISHER_BADGE.get());
                                output.accept(ItemRegistry.LUMBERJACK_BADGE.get());
                                output.accept(ItemRegistry.SMELTER_BADGE.get());
                                output.accept(ItemRegistry.COOK_BADGE.get());
                                output.accept(ItemRegistry.JANITOR_BADGE.get());
                                output.accept(ItemRegistry.MILKMAID_BADGE.get());
                                output.accept(ItemRegistry.GARDENER_BADGE.get());
                                output.accept(ItemRegistry.BREWER_BADGE.get());
                                output.accept(ItemRegistry.BUTCHER_BADGE.get());
                                output.accept(ItemRegistry.ARTISAN_BADGE.get());
                                output.accept(ItemRegistry.METALWORKER_BADGE.get());
                                output.accept(ItemRegistry.GOLEM_BUNKHOUSE.get());
                                output.accept(ItemRegistry.GOLEM_APARTMENT.get());
                                output.accept(ItemRegistry.WORK_ORDER.get());
                                output.accept(ItemRegistry.STICK_STAND.get());
                                output.accept(ItemRegistry.DIRECTORY_BOARD.get());
                                output.accept(ItemRegistry.LUNCH_CART.get());
                                output.accept(ItemRegistry.IMMORTAL_SOUL.get());
                                // Reforging items, in tier order so the tab
                                // reads as a progression rather than a jumble.
                                output.accept(ItemRegistry.WOOD_REFORGE.get());
                                output.accept(ItemRegistry.STONE_REFORGE.get());
                                output.accept(ItemRegistry.IRON_REFORGE.get());
                                output.accept(ItemRegistry.GOLD_REFORGE.get());
                                output.accept(ItemRegistry.OBSIDIAN_REFORGE.get());
                                output.accept(ItemRegistry.DIAMOND_REFORGE.get());
                                output.accept(ItemRegistry.NETHERITE_REFORGE.get());
                        })
                        .build()
        );
    }
    public static final Supplier<Item> STRAW_HAT = registerItem("straw_hat", () -> new Item(new Item.Properties()
            .stacksTo(1)));
    public static final Supplier<? extends Item> BREEDER_GOLEM_SPAWN_EGG = registerItem("breeder_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.BREEDERGOLEM, 0xD9C27A, 0xE8809B, new Item.Properties()));
    public static final Supplier<? extends Item> STOCK_GOLEM_SPAWN_EGG = registerItem("stock_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.STOCKGOLEM, 0xD9C27A, 0x8B5A2B, new Item.Properties()));
    public static final Supplier<? extends Item> BREEDER_BADGE = registerItem("breeder_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.BREEDERGOLEM::get));
    public static final Supplier<? extends Item> STOCK_BADGE = registerItem("stock_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.STOCKGOLEM::get));
    public static final Supplier<? extends Item> MINER_BADGE = registerItem("miner_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.MINERGOLEM::get));
    public static final Supplier<? extends Item> EXCAVATOR_BADGE = registerItem("excavator_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.EXCAVATORGOLEM::get));
    public static final Supplier<? extends Item> BEEKEEPER_BADGE = registerItem("beekeeper_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.BEEKEEPERGOLEM::get));
    public static final Supplier<? extends Item> FISHER_BADGE = registerItem("fisher_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.FISHERGOLEM::get));
    public static final Supplier<? extends Item> LUMBERJACK_BADGE = registerItem("lumberjack_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.LUMBERJACKGOLEM::get));
    public static final Supplier<? extends Item> SMELTER_BADGE = registerItem("smelter_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.SMELTERGOLEM::get));
    public static final Supplier<? extends Item> COOK_BADGE = registerItem("cook_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.COOKGOLEM::get));
    public static final Supplier<? extends Item> EXCAVATOR_GOLEM_SPAWN_EGG = registerItem("excavator_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.EXCAVATORGOLEM, 0xD9C27A, 0xC2B280, new Item.Properties()));
    public static final Supplier<? extends Item> COOK_GOLEM_SPAWN_EGG = registerItem("cook_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.COOKGOLEM, 0xD9C27A, 0xF2EFE6, new Item.Properties()));
    public static final Supplier<? extends Item> SMELTER_GOLEM_SPAWN_EGG = registerItem("smelter_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.SMELTERGOLEM, 0xD9C27A, 0xB35A2E, new Item.Properties()));
    public static final Supplier<? extends Item> LUMBERJACK_GOLEM_SPAWN_EGG = registerItem("lumberjack_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.LUMBERJACKGOLEM, 0xD9C27A, 0x2E6B2E, new Item.Properties()));
    public static final Supplier<? extends Item> FISHER_GOLEM_SPAWN_EGG = registerItem("fisher_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.FISHERGOLEM, 0xD9C27A, 0x3E6B8F, new Item.Properties()));
    public static final Supplier<? extends Item> BEEKEEPER_GOLEM_SPAWN_EGG = registerItem("beekeeper_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.BEEKEEPERGOLEM, 0xD9C27A, 0xF4B41B, new Item.Properties()));
    public static final Supplier<? extends Item> MINER_GOLEM_SPAWN_EGG = registerItem("miner_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.MINERGOLEM, 0xD9C27A, 0x6B6B70, new Item.Properties()));
    public static final Supplier<? extends Item> JANITOR_GOLEM_SPAWN_EGG = registerItem("janitor_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.JANITORGOLEM, 0xD9C27A, 0x4F7A72, new Item.Properties()));
    public static final Supplier<? extends Item> MILKMAID_GOLEM_SPAWN_EGG = registerItem("milkmaid_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.MILKMAIDGOLEM, 0xD9C27A, 0xBFD7EA, new Item.Properties()));
    public static final Supplier<? extends Item> GARDENER_GOLEM_SPAWN_EGG = registerItem("gardener_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.GARDENERGOLEM, 0xD9C27A, 0x5FA050, new Item.Properties()));
    public static final Supplier<? extends Item> BREWER_GOLEM_SPAWN_EGG = registerItem("brewer_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.BREWERGOLEM, 0xD9C27A, 0x7E4FA0, new Item.Properties()));
    public static final Supplier<? extends Item> BREWER_BADGE = registerItem("brewer_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.BREWERGOLEM::get));
    public static final Supplier<? extends Item> BUTCHER_GOLEM_SPAWN_EGG = registerItem("butcher_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.BUTCHERGOLEM, 0xD9C27A, 0xB03A2E, new Item.Properties()));
    public static final Supplier<? extends Item> BUTCHER_BADGE = registerItem("butcher_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.BUTCHERGOLEM::get));
    public static final Supplier<? extends Item> ARTISAN_GOLEM_SPAWN_EGG = registerItem("artisan_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.ARTISANGOLEM, 0xD9C27A, 0x4A6FA5, new Item.Properties()));
    public static final Supplier<? extends Item> ARTISAN_BADGE = registerItem("artisan_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.ARTISANGOLEM::get));
    public static final Supplier<? extends Item> METALWORKER_GOLEM_SPAWN_EGG = registerItem("metalworker_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.METALWORKERGOLEM, 0xD9C27A, 0x7A5C99, new Item.Properties()));
    public static final Supplier<? extends Item> METALWORKER_BADGE = registerItem("metalworker_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.METALWORKERGOLEM::get));
    public static final Supplier<? extends Item> JANITOR_BADGE = registerItem("janitor_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.JANITORGOLEM::get));
    public static final Supplier<? extends Item> MILKMAID_BADGE = registerItem("milkmaid_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.MILKMAIDGOLEM::get));
    public static final Supplier<? extends Item> GARDENER_BADGE = registerItem("gardener_badge",
            () -> new org.hero.strawgolem.item.ProfessionBadgeItem(new Item.Properties(), EntityRegistry.GARDENERGOLEM::get));
    public static final Supplier<? extends Item> LUNCH_CART = registerItem("lunch_cart",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.LUNCH_CART.get(), new Item.Properties()));
    public static final Supplier<? extends Item> GOLEM_BUNKHOUSE = registerItem("golem_bunkhouse",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.GOLEM_BUNKHOUSE.get(), new Item.Properties()));
    public static final Supplier<? extends Item> WORK_ORDER = registerItem("work_order",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.WORK_ORDER.get(), new Item.Properties()));
    public static final Supplier<? extends Item> GOLEM_APARTMENT = registerItem("golem_apartment",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.GOLEM_APARTMENT.get(), new Item.Properties()));
    public static final Supplier<? extends Item> DIRECTORY_BOARD = registerItem("directory_board",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.DIRECTORY_BOARD.get(), new Item.Properties()));
    public static final Supplier<? extends Item> STICK_STAND = registerItem("stick_stand",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.STICK_STAND.get(), new Item.Properties()));
    public static final Supplier<? extends Item> GHAST_TEAR_BLOCK = registerItem("ghast_tear_block",
            () -> new net.minecraft.world.item.BlockItem(
                    org.hero.strawgolem.registry.BlockRegistry.GHAST_TEAR_BLOCK.get(),
                    new Item.Properties()));

    /**
     * Baba Yaga's Promise: one death undone, at a price you feel.
     *
     * <p>Spent at a lodge to bring back a golem that died this session. An
     * Immortal Soul (one, cheap) stops a Master dying of old age in the first
     * place and is always the better bargain; this is for when you were too
     * late, and it is priced so that being too late stings.
     */
    public static final Supplier<? extends Item> BABA_YAGAS_PROMISE = registerItem("baba_yagas_promise",
            () -> new org.hero.strawgolem.item.BabaYagasPromiseItem(new Item.Properties()
                    .stacksTo(4)
                    .rarity(net.minecraft.world.item.Rarity.EPIC)
                    .fireResistant()));

    public static final Supplier<? extends Item> IMMORTAL_SOUL = registerItem("immortal_soul",
            () -> new org.hero.strawgolem.item.ImmortalSoulItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final Supplier<? extends Item> GOLEM_RETRAINER = registerItem("golem_retrainer",
            () -> new org.hero.strawgolem.item.GolemRetrainerItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<? extends Item> GOLEM_CARRIER = registerItem("golem_carrier",
            () -> new org.hero.strawgolem.item.GolemCarrierItem(new Item.Properties().stacksTo(1).fireResistant()));
    public static final Supplier<? extends Item> TRAFFIC_CONE = registerItem("traffic_cone",
            () -> new org.hero.strawgolem.item.TrafficConeItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<? extends Item> GOLEM_BACKPACK = registerItem("golem_backpack",
            () -> new org.hero.strawgolem.item.GolemBackpackItem(new Item.Properties()));
    public static final Supplier<? extends Item> GOLEM_DIRECTORY = registerItem("golem_directory",
            () -> new org.hero.strawgolem.item.GolemDirectoryItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<? extends Item> WOOD_REFORGE = registerItem("wood_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.WOOD));
    public static final Supplier<? extends Item> STONE_REFORGE = registerItem("stone_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.STONE));
    public static final Supplier<? extends Item> IRON_REFORGE = registerItem("iron_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.IRON));
    public static final Supplier<? extends Item> GOLD_REFORGE = registerItem("gold_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.GOLD));
    public static final Supplier<? extends Item> OBSIDIAN_REFORGE = registerItem("obsidian_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.OBSIDIAN));
    public static final Supplier<? extends Item> DIAMOND_REFORGE = registerItem("diamond_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.DIAMOND));
    public static final Supplier<? extends Item> NETHERITE_REFORGE = registerItem("netherite_reforge",
            () -> new org.hero.strawgolem.item.GolemReforgeItem(new Item.Properties(),
                    org.hero.strawgolem.golem.GolemMaterial.NETHERITE));

    private static <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
        return Constants.COMMON_PLATFORM.registerItem(name, item);
    }

}
