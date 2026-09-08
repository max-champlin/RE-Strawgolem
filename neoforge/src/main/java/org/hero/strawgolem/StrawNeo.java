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
        registerClaimReset(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
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
     * Empty the goal-level claim boards as a world comes up.
     *
     * <p>They are static, so in single player they outlive the world that filled
     * them. Their entries expire against {@code level.getGameTime()}, which
     * restarts on the new world's own clock - so claims made at game time
     * 60,000,000 look 60 million ticks from expiring in a world that is at 1,000,
     * and the crops and chests they name stay locked for the session.
     *
     * <p>On START rather than STOP on purpose: a crash, a kill, or a power cut
     * never reaches a shutdown hook, and those are exactly the sessions after
     * which you want a clean board.
     */
    private void registerClaimReset(net.neoforged.bus.api.IEventBus gameBus) {
        gameBus.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) ->
                org.hero.strawgolem.golem.goals.ClaimScope.clearAll());
        registerBoardIndex(gameBus);
    }

    /**
     * Index every work order board in a chunk as that chunk loads.
     *
     * <p>The block entity also announces itself from {@code clearRemoved}, but
     * that is only reliably the PLACEMENT path. A board that was already in the
     * world when you logged in comes back through chunk loading, and if that
     * path does not announce it then the board is invisible to golems: marking
     * an area, setting a crew and choosing a filter all work perfectly, the
     * board just never reaches anybody. That failure is completely silent,
     * which is the worst kind, so this hook does not depend on the other one
     * being right - the index is a Set, so announcing twice costs nothing.
     */
    private void registerBoardIndex(net.neoforged.bus.api.IEventBus gameBus) {
        gameBus.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
            if (!(event.getLevel() instanceof net.minecraft.world.level.Level level) || level.isClientSide) {
                return;
            }
            if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) {
                return;
            }
            // Only block entities that already exist. ChunkAccess exposes the
            // POSITIONS of pending ones too, but resolving those would force
            // every block entity in the chunk to deserialize just so we can ask
            // its type - a real cost on chunk load, paid for a block almost no
            // chunk contains. Placement and load both go through clearRemoved;
            // this is the belt to that pair of braces.
            for (net.minecraft.world.level.block.entity.BlockEntity be
                    : chunk.getBlockEntities().values()) {
                if (be instanceof org.hero.strawgolem.block.WorkOrderBlockEntity) {
                    org.hero.strawgolem.block.WorkOrders.register(level, be.getBlockPos());
                }
            }
        });
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
            // Sleepers are NOT entities - a checked-in golem is NBT inside a
            // dormitory block and never appears in getAllEntities(). Counting
            // only entities made a full apartment look like a vanished
            // workforce, which on 2026-09-08 had the player believing 36 golems
            // were lost. Any block holding a "Sleepers" list counts, found by
            // NBT rather than by block name.
            int asleep = org.hero.strawgolem.block.BunkhouseBlockEntity
                    .sleepingEverywhere();
            int found = loaded + asleep;
            Constants.LOG.info("Golem headcount at world load: {} on the roster, "
                            + "{} awake, {} asleep in dormitories, {} accounted for{}",
                    known, loaded, asleep, found,
                    per.length() > 0 ? " (" + per + ")" : "");
            // Say something LOUD when the numbers disagree.
            //
            // The roster is the record of who should exist; awake plus asleep is
            // who actually does. A quiet mismatch is the difference between
            // noticing a loss today and discovering it a week later, and it is
            // the one number nobody checks unless it shouts.
            // Deliberately NOT an alarm.
            //
            // The first version shouted GOLEM HEADCOUNT MISMATCH whenever the
            // roster exceeded awake-plus-asleep, and its first live firing was
            // a false positive: the player had every golem safely in bags for a
            // rebuild. A shortfall here is the NORMAL state, not a fault - a
            // golem can be bagged in any of a dozen mods' capture items, in an
            // unloaded chunk, or in another dimension, and none of those are
            // visible from a headcount.
            //
            // The roster is the authoritative record: it is persistent and
            // survives all three. So report the gap as context and name the
            // innocent explanations, rather than training the player to ignore
            // a message that cannot tell safe from lost. A real loss shows up as
            // the ROSTER shrinking, which is a different measurement and wants
            // its own check.
            if (known > found) {
                Constants.LOG.info("{} of {} not currently in the world - normal if "
                                + "they are bagged, in unloaded chunks, or in another "
                                + "dimension. The roster is the count that matters.",
                        known - found, known);
            }
            // Boards indexed, alongside the headcount, because "the crew is
            // ignoring my order" and "no board was ever indexed" look identical
            // from inside the game.
            Constants.LOG.info("Work order boards indexed: {}",
                    org.hero.strawgolem.block.WorkOrders.count());
        });
    }

}