package org.hero.strawgolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.hero.strawgolem.registry.BlockRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * The Lunch Cart: a shared pantry golems eat from when hunger is enabled. Food
 * comes from here, never from a golem's work chest - so a Cook won't eat his
 * own ingredients and a Janitor won't eat what he collected. Accepts food from
 * hoppers, or right-click a stack in; open it (empty hand) to take food back out.
 */
public class LunchCartBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SIZE = 27;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public LunchCartBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.LUNCH_CART_BLOCK_ENTITY.get(), pos, state);
    }

    public static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    /** Total servings on hand, for the status message. */
    public int servings() {
        int n = 0;
        for (ItemStack stack : items) {
            if (isFood(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Removes one food item for a hungry golem; empty if the cart is bare. */
    public ItemStack takeOneFood() {
        for (int i = 0; i < items.size(); i++) {
            if (isFood(items.get(i))) {
                return removeItem(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Adds as much of a stack as fits; returns the leftover. */
    public ItemStack stock(ItemStack incoming) {
        if (!isFood(incoming)) {
            return incoming;
        }
        for (int i = 0; i < items.size() && !incoming.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, incoming.copyAndClear());
            } else if (ItemStack.isSameItemSameComponents(slot, incoming)) {
                int move = Math.min(incoming.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move);
                incoming.shrink(move);
            }
        }
        setChanged();
        return incoming;
    }

    // --- Container (gives vanilla hoppers food insertion for free) ---

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isFood(stack);
    }

    // --- MenuProvider: open like a chest to retrieve food ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.strawgolem.lunch_cart");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return ChestMenu.threeRows(containerId, playerInventory, this);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    NonNullList<ItemStack> contents() {
        return items;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }
}
