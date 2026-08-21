package org.hero.strawgolem.platform;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.fml.loading.FMLPaths;
import org.hero.strawgolem.StrawNeo;
import org.hero.strawgolem.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.nio.file.Path;
import java.util.function.Supplier;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntity(String id, Supplier<BlockEntityType<T>> blockEntityType) {
        return StrawNeo.BLOCK_ENTITIES.register(id, blockEntityType);
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String id, Supplier<T> block) {
        return StrawNeo.BLOCKS.register(id, block);
    }

    @Override
    public <T extends BlockEntity> Supplier<BlockEntityType<T>> registerBlockEntity(String id,
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            Supplier<? extends Block> block) {
        return StrawNeo.BLOCK_ENTITIES.register(id,
                () -> BlockEntityType.Builder.<T>of(factory::apply, block.get()).build(null));
    }

    @Override
    public <T extends Entity> Supplier<EntityType<T>> registerEntity(String id, Supplier<EntityType<T>> entity) {
        return StrawNeo.ENTITIES.register(id, entity);
    }

    @Override
    public <T extends ParticleOptions> Supplier<ParticleType<T>> registerParticle(String id, Supplier<ParticleType<T>> particle) {
        return StrawNeo.PARTICLES.register(id, particle);
    }

    @Override
    public <T extends ArmorMaterial> Holder<T> registerArmorMaterial(String id, Supplier<T> armorMaterial) {
        return (Holder<T>) StrawNeo.ARMOR_MATERIALS.register(id, armorMaterial);
    }

    @Override
    public <T extends Item> Supplier<T> registerItem(String id, Supplier<T> item) {
        return StrawNeo.ITEMS.register(id, item);
    }

    @Override
    public <T extends SoundEvent> Supplier<T> registerSound(String id, Supplier<T> sound) {
        return StrawNeo.SOUND_EVENTS.register(id, sound);
    }

    @Override
    public <T extends CreativeModeTab> Supplier<T> registerCreativeModeTab(String id, Supplier<T> tab) {
        return StrawNeo.CREATIVE_TABS.register(id, tab);
    }

    @Override
    public <E extends Mob> Supplier<SpawnEggItem> makeSpawnEggFor(Supplier<EntityType<E>> entityType, int primaryEggColour, int secondaryEggColour, Item.Properties itemProperties) {
        return () -> new DeferredSpawnEggItem(entityType, primaryEggColour, secondaryEggColour, itemProperties);
    }

    @Override
    public CreativeModeTab.Builder newCreativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public boolean isItemReceiver(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos) {
        return level instanceof net.minecraft.world.level.Level lvl
                && lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    @Override
    public net.minecraft.world.item.ItemStack insertItem(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        if (level instanceof net.minecraft.world.level.Level lvl) {
            var handler = lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                return net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
            }
        }
        return stack;
    }

    @Override
    public net.minecraft.world.item.ItemStack insertItemSimulate(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        if (level instanceof net.minecraft.world.level.Level lvl) {
            var handler = lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                return net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true);
            }
        }
        return stack;
    }

    @Override
    public void requestRoster() {
        org.hero.strawgolem.network.StrawNetwork.requestRoster();
    }

    @Override
    public boolean acceptsItem(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof net.minecraft.world.level.Level lvl)) {
            return true;
        }
        var handler = lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            return true; // not a capability inventory - nothing to judge
        }
        // isItemValid asks the FILTER, not the free space, so a storage chest
        // locked to a different item answers false on every slot even when empty.
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.isItemValid(slot, stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> snapshotStacks(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos) {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        if (level instanceof net.minecraft.world.level.Level lvl) {
            var handler = lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    net.minecraft.world.item.ItemStack s = handler.getStackInSlot(slot);
                    if (!s.isEmpty()) {
                        out.add(s.copy());
                    }
                }
            }
        }
        return out;
    }

    @Override
    public net.minecraft.world.item.ItemStack extractMatching(net.minecraft.world.level.LevelReader level, net.minecraft.core.BlockPos pos, java.util.function.Predicate<net.minecraft.world.item.ItemStack> predicate, int maxCount, boolean simulate) {
        if (level instanceof net.minecraft.world.level.Level lvl) {
            var handler = lvl.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    if (predicate.test(handler.getStackInSlot(slot))) {
                        net.minecraft.world.item.ItemStack got = handler.extractItem(slot, maxCount, simulate);
                        if (!got.isEmpty()) {
                            return got;
                        }
                    }
                }
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
