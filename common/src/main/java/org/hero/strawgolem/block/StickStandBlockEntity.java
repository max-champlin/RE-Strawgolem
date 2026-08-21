package org.hero.strawgolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.hero.strawgolem.registry.BlockRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The umbrella stand for Foreman's Sticks: holds up to six, names and ledger
 * pages intact, so preset "recipe card" sticks have somewhere to live.
 */
public class StickStandBlockEntity extends BlockEntity {
    public static final int CAPACITY = 6;

    private final List<ItemStack> sticks = new ArrayList<>();

    public StickStandBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.STICK_STAND_BLOCK_ENTITY.get(), pos, state);
    }

    public int count() {
        return sticks.size();
    }

    public List<ItemStack> view() {
        return sticks;
    }

    /** Adds one stick (already split off by the caller). */
    public boolean insert(ItemStack single) {
        if (sticks.size() >= CAPACITY || single.isEmpty()) {
            return false;
        }
        sticks.add(single);
        setChanged();
        return true;
    }

    /** Removes and returns the most recently stored stick. */
    public ItemStack takeLast() {
        if (sticks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stick = sticks.remove(sticks.size() - 1);
        setChanged();
        return stick;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (ItemStack stick : sticks) {
            list.add(stick.save(registries));
        }
        tag.put("Sticks", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sticks.clear();
        ListTag list = tag.getList("Sticks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(sticks::add);
        }
    }
}
