package org.hero.strawgolem.registry;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.golem.BreederGolem;
import org.hero.strawgolem.golem.BeekeeperGolem;
import org.hero.strawgolem.golem.FisherGolem;
import org.hero.strawgolem.golem.LumberjackGolem;
import org.hero.strawgolem.golem.CookGolem;
import org.hero.strawgolem.golem.ExcavatorGolem;
import org.hero.strawgolem.golem.SmelterGolem;
import org.hero.strawgolem.golem.MinerGolem;
import org.hero.strawgolem.golem.StockGolem;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class EntityRegistry {
    public static void init() {}

    public static final Supplier<EntityType<StrawGolem>> STRAWGOLEM = registerEntity("strawgolem", StrawGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<BreederGolem>> BREEDERGOLEM = registerEntity("breeder_golem", BreederGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<StockGolem>> STOCKGOLEM = registerEntity("stock_golem", StockGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<MinerGolem>> MINERGOLEM = registerEntity("miner_golem", MinerGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<BeekeeperGolem>> BEEKEEPERGOLEM = registerEntity("beekeeper_golem", BeekeeperGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<FisherGolem>> FISHERGOLEM = registerEntity("fisher_golem", FisherGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<LumberjackGolem>> LUMBERJACKGOLEM = registerEntity("lumberjack_golem", LumberjackGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<SmelterGolem>> SMELTERGOLEM = registerEntity("smelter_golem", SmelterGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<ExcavatorGolem>> EXCAVATORGOLEM = registerEntity("excavator_golem", ExcavatorGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<CookGolem>> COOKGOLEM = registerEntity("cook_golem", CookGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.JanitorGolem>> JANITORGOLEM = registerEntity("janitor_golem", org.hero.strawgolem.golem.JanitorGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.MilkmaidGolem>> MILKMAIDGOLEM = registerEntity("milkmaid_golem", org.hero.strawgolem.golem.MilkmaidGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.GardenerGolem>> GARDENERGOLEM = registerEntity("gardener_golem", org.hero.strawgolem.golem.GardenerGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.BrewerGolem>> BREWERGOLEM = registerEntity("brewer_golem", org.hero.strawgolem.golem.BrewerGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.ButcherGolem>> BUTCHERGOLEM = registerEntity("butcher_golem", org.hero.strawgolem.golem.ButcherGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.ArtisanGolem>> ARTISANGOLEM = registerEntity("artisan_golem", org.hero.strawgolem.golem.ArtisanGolem::new, 0.6f, 0.9f);
    public static final Supplier<EntityType<org.hero.strawgolem.golem.MetalworkerGolem>> METALWORKERGOLEM = registerEntity("metalworker_golem", org.hero.strawgolem.golem.MetalworkerGolem::new, 0.6f, 0.9f);

    public static void registerEntityAttributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> registrar) {
        registrar.accept(EntityRegistry.STRAWGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.BREEDERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.STOCKGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.MINERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.BEEKEEPERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.FISHERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.LUMBERJACKGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.SMELTERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.EXCAVATORGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.COOKGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.JANITORGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.MILKMAIDGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.GARDENERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.BREWERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.BUTCHERGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.ARTISANGOLEM.get(), StrawGolem.createAttributes().build());
        registrar.accept(EntityRegistry.METALWORKERGOLEM.get(), StrawGolem.createAttributes().build());
    }
    private static <T extends Mob> Supplier<EntityType<T>> registerEntity(String name, EntityType.EntityFactory<T> entity, float width, float height) {
        return Constants.COMMON_PLATFORM.registerEntity(name, () -> EntityType.Builder.of(entity, MobCategory.CREATURE).sized(width, height).build(name));
    }
}
