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
                                output.accept(ItemRegistry.BREEDER_HAT.get());
                                output.accept(ItemRegistry.STOCK_HAT.get());
                                output.accept(ItemRegistry.MINER_HAT.get());
                                output.accept(ItemRegistry.EXCAVATOR_HAT.get());
                                output.accept(ItemRegistry.BEEKEEPER_HAT.get());
                                output.accept(ItemRegistry.FISHER_HAT.get());
                                output.accept(ItemRegistry.LUMBERJACK_HAT.get());
                                output.accept(ItemRegistry.SMELTER_HAT.get());
                                output.accept(ItemRegistry.COOK_HAT.get());
                                output.accept(ItemRegistry.JANITOR_HAT.get());
                                output.accept(ItemRegistry.MILKMAID_HAT.get());
                                output.accept(ItemRegistry.GARDENER_HAT.get());
                                output.accept(ItemRegistry.BREWER_HAT.get());
                                output.accept(ItemRegistry.BUTCHER_HAT.get());
                                output.accept(ItemRegistry.ARTISAN_HAT.get());
                                output.accept(ItemRegistry.METALWORKER_HAT.get());
                                output.accept(ItemRegistry.GOLEM_BUNKHOUSE.get());
                                output.accept(ItemRegistry.STICK_STAND.get());
                                output.accept(ItemRegistry.DIRECTORY_BOARD.get());
                                output.accept(ItemRegistry.LUNCH_CART.get());
                                output.accept(ItemRegistry.IMMORTAL_SOUL.get());
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
    public static final Supplier<? extends Item> BREEDER_HAT = registerItem("breeder_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.BREEDERGOLEM::get));
    public static final Supplier<? extends Item> STOCK_HAT = registerItem("stock_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.STOCKGOLEM::get));
    public static final Supplier<? extends Item> MINER_HAT = registerItem("miner_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.MINERGOLEM::get));
    public static final Supplier<? extends Item> EXCAVATOR_HAT = registerItem("excavator_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.EXCAVATORGOLEM::get));
    public static final Supplier<? extends Item> BEEKEEPER_HAT = registerItem("beekeeper_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.BEEKEEPERGOLEM::get));
    public static final Supplier<? extends Item> FISHER_HAT = registerItem("fisher_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.FISHERGOLEM::get));
    public static final Supplier<? extends Item> LUMBERJACK_HAT = registerItem("lumberjack_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.LUMBERJACKGOLEM::get));
    public static final Supplier<? extends Item> SMELTER_HAT = registerItem("smelter_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.SMELTERGOLEM::get));
    public static final Supplier<? extends Item> COOK_HAT = registerItem("cook_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.COOKGOLEM::get));
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
    public static final Supplier<? extends Item> BREWER_HAT = registerItem("brewer_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.BREWERGOLEM::get));
    public static final Supplier<? extends Item> BUTCHER_GOLEM_SPAWN_EGG = registerItem("butcher_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.BUTCHERGOLEM, 0xD9C27A, 0xB03A2E, new Item.Properties()));
    public static final Supplier<? extends Item> BUTCHER_HAT = registerItem("butcher_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.BUTCHERGOLEM::get));
    public static final Supplier<? extends Item> ARTISAN_GOLEM_SPAWN_EGG = registerItem("artisan_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.ARTISANGOLEM, 0xD9C27A, 0x4A6FA5, new Item.Properties()));
    public static final Supplier<? extends Item> ARTISAN_HAT = registerItem("artisan_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.ARTISANGOLEM::get));
    public static final Supplier<? extends Item> METALWORKER_GOLEM_SPAWN_EGG = registerItem("metalworker_golem_spawn_egg",
            Services.PLATFORM.makeSpawnEggFor(EntityRegistry.METALWORKERGOLEM, 0xD9C27A, 0x7A5C99, new Item.Properties()));
    public static final Supplier<? extends Item> METALWORKER_HAT = registerItem("metalworker_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.METALWORKERGOLEM::get));
    public static final Supplier<? extends Item> JANITOR_HAT = registerItem("janitor_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.JANITORGOLEM::get));
    public static final Supplier<? extends Item> MILKMAID_HAT = registerItem("milkmaid_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.MILKMAIDGOLEM::get));
    public static final Supplier<? extends Item> GARDENER_HAT = registerItem("gardener_hat",
            () -> new org.hero.strawgolem.item.ProfessionHatItem(new Item.Properties(), EntityRegistry.GARDENERGOLEM::get));
    public static final Supplier<? extends Item> LUNCH_CART = registerItem("lunch_cart",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.LUNCH_CART.get(), new Item.Properties()));
    public static final Supplier<? extends Item> GOLEM_BUNKHOUSE = registerItem("golem_bunkhouse",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.GOLEM_BUNKHOUSE.get(), new Item.Properties()));
    public static final Supplier<? extends Item> DIRECTORY_BOARD = registerItem("directory_board",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.DIRECTORY_BOARD.get(), new Item.Properties()));
    public static final Supplier<? extends Item> STICK_STAND = registerItem("stick_stand",
            () -> new net.minecraft.world.item.BlockItem(org.hero.strawgolem.registry.BlockRegistry.STICK_STAND.get(), new Item.Properties()));
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
    private static <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
        return Constants.COMMON_PLATFORM.registerItem(name, item);
    }

}
