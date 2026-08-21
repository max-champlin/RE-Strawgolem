package org.hero.strawgolem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.hero.strawgolem.item.GolemCarrierItem;

import java.util.Iterator;

/**
 * "Humans gonna human" insurance for the Golem Bindle. Fire/lava is handled by
 * the item being fireResistant; these handlers cover the rest so a loaded
 * bindle never eats a golem:
 *   - about to despawn on the ground -> drop the golem safely instead
 *   - player dies (including a void plunge with it in their pack) -> the loaded
 *     bindle is pulled out of the death drops and handed back on respawn
 * The only thing left that can lose a golem is dropping the bindle straight
 * into the void, which nothing survives.
 */
public final class GolemCarrierEvents {
    private static final String STASH_KEY = "strawgolem_saved_bindles";

    private GolemCarrierEvents() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(GolemCarrierEvents::onItemExpire);
        gameBus.addListener(GolemCarrierEvents::onPlayerDrops);
        gameBus.addListener(GolemCarrierEvents::onClone);
    }

    /** A loaded bindle about to despawn sets its golem down safely instead of vanishing it. */
    public static void onItemExpire(ItemExpireEvent event) {
        ItemEntity ie = event.getEntity();
        ItemStack stack = ie.getItem();
        if (ie.level().isClientSide
                || !(stack.getItem() instanceof GolemCarrierItem)
                || !GolemCarrierItem.isFull(stack)) {
            return;
        }
        if (!GolemCarrierItem.releaseInto(stack, ie.level(), ie.getX(), ie.getY(), ie.getZ())) {
            // Couldn't place it right now - keep the item alive rather than lose the golem.
            event.setExtraLife(6000);
        }
    }

    /** Pull loaded bindles out of a player's death drops so they aren't lost (void-safe). */
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ListTag stash = new ListTag();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (stack.getItem() instanceof GolemCarrierItem && GolemCarrierItem.isFull(stack)) {
                stash.add(stack.saveOptional(player.registryAccess()));
                it.remove();
            }
        }
        if (!stash.isEmpty()) {
            player.getPersistentData().put(STASH_KEY, stash);
        }
    }

    /** On respawn, hand any stashed bindles back to the new player. */
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        CompoundTag oldData = event.getOriginal().getPersistentData();
        if (!oldData.contains(STASH_KEY)) {
            return;
        }
        ListTag stash = oldData.getList(STASH_KEY, Tag.TAG_COMPOUND);
        Player newPlayer = event.getEntity();
        for (int i = 0; i < stash.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(newPlayer.registryAccess(), stash.getCompound(i));
            if (!stack.isEmpty() && !newPlayer.getInventory().add(stack)) {
                newPlayer.drop(stack, false);
            }
        }
        oldData.remove(STASH_KEY);
    }
}
