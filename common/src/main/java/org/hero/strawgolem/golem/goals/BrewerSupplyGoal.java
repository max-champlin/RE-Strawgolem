package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.BrewerGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Keeps brewing stands working: blaze powder into the fuel slot, water bottles
 * into empty bottle slots, and - once bottles are in - the first chest
 * ingredient that actually brews with them (nether wart preferred, so the
 * awkward base gets made before flavoring). Brewing stand slots: 0-2 bottles,
 * 3 ingredient, 4 fuel.
 */
public class BrewerSupplyGoal extends Goal {
    private static final int SCAN_COOLDOWN = 40;
    private static final int MODE_FUEL = 0;
    private static final int MODE_BOTTLE = 1;
    private static final int MODE_INGREDIENT = 2;

    private final BrewerGolem golem;
    private BlockPos standPos;
    private int mode;
    private boolean fetched;
    private boolean done;
    private int scanCooldown;

    public BrewerSupplyGoal(BrewerGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private BrewingStandBlockEntity standAt(BlockPos pos) {
        return golem.level().getBlockEntity(pos) instanceof BrewingStandBlockEntity s ? s : null;
    }

    private static boolean hasBottles(BrewingStandBlockEntity stand) {
        return !stand.getItem(0).isEmpty() || !stand.getItem(1).isEmpty() || !stand.getItem(2).isEmpty();
    }

    private static boolean hasEmptyBottleSlot(BrewingStandBlockEntity stand) {
        return stand.getItem(0).isEmpty() || stand.getItem(1).isEmpty() || stand.getItem(2).isEmpty();
    }

    /** An ingredient that brews with at least one bottle currently in the stand. */
    private Predicate<ItemStack> ingredientFor(BrewingStandBlockEntity stand) {
        return s -> {
            if (s.isEmpty()) {
                return false;
            }
            for (int i = 0; i < 3; i++) {
                ItemStack bottle = stand.getItem(i);
                if (!bottle.isEmpty() && golem.level().potionBrewing().hasMix(bottle, s)) {
                    return true;
                }
            }
            return false;
        };
    }

    private boolean plan() {
        for (BlockPos pos : golem.findStands()) {
            BrewingStandBlockEntity stand = standAt(pos);
            if (stand == null) {
                continue;
            }
            if (hasBottles(stand) && stand.getItem(4).isEmpty()
                    && golem.chestHas(s -> s.is(Items.BLAZE_POWDER))) {
                standPos = pos;
                mode = MODE_FUEL;
                return true;
            }
            if (hasEmptyBottleSlot(stand) && golem.chestHas(BrewerGolem::isWaterBottle)) {
                standPos = pos;
                mode = MODE_BOTTLE;
                return true;
            }
            if (stand.getItem(3).isEmpty() && hasBottles(stand)
                    && golem.chestHas(ingredientFor(stand))) {
                standPos = pos;
                mode = MODE_INGREDIENT;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canUse() {
        if (!golem.getMainHandItem().isEmpty() || !golem.hasDepositChest()
                || !ContainerHelper.isContainer(golem, golem.getPriorityPos())) {
            return false;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_COOLDOWN;
        return plan();
    }

    @Override
    public boolean canContinueToUse() {
        return !done && standPos != null && standAt(standPos) != null
                && ContainerHelper.isContainer(golem, golem.getPriorityPos());
    }

    @Override
    public void start() {
        fetched = false;
        done = false;
        moveTo(golem.getPriorityPos());
    }

    @Override
    public void stop() {
        standPos = null;
        fetched = false;
        done = false;
        golem.getNavigation().stop();
    }

    private void moveTo(BlockPos pos) {
        golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (standPos == null) {
            return;
        }
        if (!fetched) {
            BlockPos chest = golem.getPriorityPos();
            golem.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            if (!ReachHelper.canReach(golem, chest)) {
                if (golem.getNavigation().isDone()) {
                    moveTo(chest);
                }
                return;
            }
            BrewingStandBlockEntity stand = standAt(standPos);
            if (stand == null) {
                done = true;
                return;
            }
            ItemStack got;
            if (mode == MODE_FUEL) {
                got = golem.takeFromChest(s -> s.is(Items.BLAZE_POWDER), 16);
            } else if (mode == MODE_BOTTLE) {
                got = golem.takeFromChest(BrewerGolem::isWaterBottle, 1);
            } else {
                Predicate<ItemStack> valid = ingredientFor(stand);
                // Nether wart first so the awkward base is brewed before flavoring.
                got = golem.takeFromChest(s -> s.is(Items.NETHER_WART) && valid.test(s), 16);
                if (got.isEmpty()) {
                    got = golem.takeFromChest(valid, 16);
                }
            }
            if (got.isEmpty()) {
                done = true;
                return;
            }
            golem.setItemSlot(EquipmentSlot.MAINHAND, got);
            fetched = true;
            moveTo(standPos);
            return;
        }
        golem.getLookControl().setLookAt(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5);
        if (!ReachHelper.canReach(golem, standPos)
                && standPos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > 9.0) {
            if (golem.getNavigation().isDone()) {
                moveTo(standPos);
            }
            return;
        }
        BrewingStandBlockEntity stand = standAt(standPos);
        ItemStack held = golem.getMainHandItem();
        if (stand != null && !held.isEmpty()) {
            if (mode == MODE_BOTTLE) {
                for (int i = 0; i < 3; i++) {
                    if (stand.getItem(i).isEmpty()) {
                        stand.setItem(i, held.split(1));
                        break;
                    }
                }
            } else {
                int slot = mode == MODE_FUEL ? 4 : 3;
                ItemStack current = stand.getItem(slot);
                if (current.isEmpty()) {
                    stand.setItem(slot, held.copy());
                    held = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(current, held)) {
                    int move = Math.min(held.getCount(), current.getMaxStackSize() - current.getCount());
                    if (move > 0) {
                        current.grow(move);
                        stand.setItem(slot, current);
                        held.shrink(move);
                    }
                }
            }
            golem.setItemSlot(EquipmentSlot.MAINHAND, held);
        }
        done = true;
    }
}
