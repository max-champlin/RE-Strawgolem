package org.hero.strawgolem.golem.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelReader;

public class ContainerHelper {
    public static boolean isContainer(LevelReader levelReader, BlockPos pos) {
        return pos != null && (levelReader.getBlockEntity(pos) instanceof Container
                || org.hero.strawgolem.platform.Services.PLATFORM.isItemReceiver(levelReader, pos));
    }

    public static boolean isContainer(Mob mob, BlockPos pos) {
        return isContainer(mob.level(), pos);
    }

    /**
     * Whether this block would take THIS item in any slot, ignoring how full it
     * is right now.
     *
     * <p>{@link #isContainer} is deliberately broad - it says "something here
     * exposes an item inventory". That is true of a great many blocks that are
     * not storage at all: an energy cube's charge slot, a machine's upgrade
     * slots, a conduit. Golems were picking those as deposit targets, walking
     * over, being refused, and walking back. This is the question that actually
     * matters when choosing a target: not "is it an inventory" but "will it take
     * what I'm holding".
     *
     * <p>Mirrors the two insert paths in Deliverer.deliver: vanilla Container
     * first (its canPlaceItem is the filter), then the platform item handler
     * (whose isItemValid is the filter). An empty stack means "no opinion".
     */
    public static boolean accepts(LevelReader level, BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        if (pos == null || stack == null || stack.isEmpty()) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.canPlaceItem(i, stack)) {
                    return true;
                }
            }
            return false;
        }
        return org.hero.strawgolem.platform.Services.PLATFORM.acceptsItem(level, pos, stack);
    }

    public static boolean accepts(Mob mob, BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        return accepts(mob.level(), pos, stack);
    }

}
