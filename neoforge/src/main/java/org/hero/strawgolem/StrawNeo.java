package org.hero.strawgolem;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hero.strawgolem.registry.EntityRegistry;
import org.hero.strawgolem.registry.ParticleRegistry;

@Mod(Constants.MODID)
public final class StrawNeo {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Constants.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Constants.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Constants.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Constants.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Constants.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Constants.MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Constants.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Constants.MODID);

    public StrawNeo(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        ITEMS.register(modEventBus);
        // Probably doing this right?
        PARTICLES.register(modEventBus);
        modEventBus.<EntityAttributeCreationEvent>addListener(event -> EntityRegistry.registerEntityAttributes(event::put));
        modEventBus.addListener(this::registerCapabilities);
        // Golem Bindle safety net (despawn / death / void) lives on the game bus.
        GolemCarrierEvents.register(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
        registerHeadcount(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
        org.hero.strawgolem.network.StrawNetwork.register(modEventBus);
//        modEventBus.<RegisterParticleProvidersEvent>addListener(event -> ParticleRegistry.registerParticleProv(event::put));

        CommonClass.init();
    }

    /**
     * Exposes the Lunch Cart's food inventory as a standard item handler so
     * modded pipes, AE2, and hoppers can insert food into it (InvWrapper
     * enforces the block's food-only rule). Lets a filtered Cook output feed
     * the whole workforce automatically.
     */
    private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                org.hero.strawgolem.registry.BlockRegistry.LUNCH_CART_BLOCK_ENTITY.get(),
                (be, side) -> new net.neoforged.neoforge.items.wrapper.InvWrapper(be));
    }

    /**
     * Log a golem headcount shortly after each world load.
     *
     * <p>On 2026-08-09 a power cut truncated an entity region mid-write and took
     * fifteen Master golems with it. Nothing said so: no crash report (the JVM
     * never got to write one), no entity-load error, and the Employee Directory
     * kept listing all fifteen from stale client copies for a full day. The loss
     * was only datable afterwards by noticing that bunkhouse release lines had
     * stopped.
     *
     * <p>One line per load makes that instantly visible instead of archaeological -
     * if the count drops to zero overnight, it is in the log the next launch.
     * Fires 200 ticks in so chunks around the player have loaded first.
     */
    private void registerHeadcount(net.neoforged.bus.api.IEventBus gameBus) {
        gameBus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (server.getTickCount() != 200) {
                return;
            }
            // The roster is the real headcount: it is persistent and covers
            // golems that are asleep in a bunkhouse, in a bindle, or in an
            // unloaded chunk. Loaded entities are reported alongside it purely
            // as context - the first version logged only entities and proudly
            // announced "0" while all fifteen were in bed.
            int known = org.hero.strawgolem.network.GolemRegistry.get(server).size();

            int loaded = 0;
            StringBuilder per = new StringBuilder();
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                int n = 0;
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e instanceof org.hero.strawgolem.golem.StrawGolem) {
                        n++;
                    }
                }
                if (n > 0) {
                    if (per.length() > 0) {
                        per.append(", ");
                    }
                    per.append(level.dimension().location()).append('=').append(n);
                    loaded += n;
                }
            }
            Constants.LOG.info("Golem headcount at world load: {} on the roster, "
                            + "{} currently loaded as entities{}",
                    known, loaded, per.length() > 0 ? " (" + per + ")" : "");
        });
    }

}