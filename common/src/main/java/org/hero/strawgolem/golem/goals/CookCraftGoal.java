package org.hero.strawgolem.golem.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.hero.strawgolem.Constants.Golem;
import org.hero.strawgolem.golem.CookGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;
import org.hero.strawgolem.golem.api.ReachHelper;
import org.hero.strawgolem.platform.Services;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crafts menu dishes: gathers a recipe's ingredients from the pantry chest,
 * walks to the crafting table, and produces the dish (plus any crafting
 * remainders like empty buckets). The stash goal returns the dish to the chest.
 */
public class CookCraftGoal extends Goal {
    private static final int SCAN_COOLDOWN = 60;
    private static final int CRAFT_TICKS = 30;
    /** Working distance for chest/table - 3 blocks, squared. */
    private static final double WORK_REACH_SQ = 9.0;

    private final CookGolem golem;
    private final Map<Item, List<RecipeHolder<CraftingRecipe>>> recipeCache = new HashMap<>();
    private BlockPos kitchenPos;
    private RecipeHolder<CraftingRecipe> plan;
    private boolean gathered;
    private boolean done;
    private int craftTicks;
    private int scanCooldown;

    public CookCraftGoal(CookGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private List<RecipeHolder<CraftingRecipe>> recipesFor(Item dish) {
        return recipeCache.computeIfAbsent(dish, item -> {
            List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();
            for (RecipeHolder<CraftingRecipe> holder : golem.level().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack result;
                try {
                    result = holder.value().getResultItem(golem.level().registryAccess());
                } catch (Throwable t) {
                    continue;
                }
                if (result != null && !result.isEmpty() && result.is(item) && golem.acceptsRecipe(holder)) {
                    matches.add(holder);
                }
            }
            return matches;
        });
    }

    /** Snapshot of pantry contents as mutable stacks. */
    private List<ItemStack> pantrySnapshot() {
        List<ItemStack> snapshot = new ArrayList<>();
        BlockPos chest = golem.getSupplyPos();
        if (golem.level().getBlockEntity(chest) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    snapshot.add(s.copy());
                }
            }
        } else {
            // Capability-only inventories (Sophisticated Storage, drawers, AE2
            // interfaces...) never implement vanilla Container, so read the whole
            // item handler instead. Previously this probed a single stack, which
            // made any such chest look almost empty and the golem never worked.
            snapshot.addAll(Services.PLATFORM.snapshotStacks(golem.level(), chest));
        }
        return snapshot;
    }

    /** Whether the snapshot satisfies every ingredient (greedy matching). */
    private boolean satisfiable(RecipeHolder<CraftingRecipe> recipe, List<ItemStack> pantry) {
        for (Ingredient ingredient : recipe.value().getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            boolean matched = false;
            for (ItemStack stack : pantry) {
                if (stack.getCount() > 0 && ingredient.test(stack)) {
                    stack.shrink(1);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private RecipeHolder<CraftingRecipe> findCookable() {
        List<ItemStack> pantry = pantrySnapshot();
        if (pantry.isEmpty()) {
            return null;
        }
        for (Item dish : golem.getMenu()) {
            for (RecipeHolder<CraftingRecipe> recipe : recipesFor(dish)) {
                List<ItemStack> copy = new ArrayList<>();
                for (ItemStack s : pantry) {
                    copy.add(s.copy());
                }
                if (satisfiable(recipe, copy)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /** Consumes the recipe's ingredients from the pantry; returns false if it no longer can. */
    private boolean consumeIngredients(RecipeHolder<CraftingRecipe> recipe) {
        BlockPos chest = golem.getSupplyPos();
        // Verify first against a snapshot, then actually remove.
        List<ItemStack> pantry = pantrySnapshot();
        if (!satisfiable(recipe, pantry)) {
            return false;
        }
        boolean vanilla = golem.level().getBlockEntity(chest) instanceof Container;
        Container container = vanilla ? (Container) golem.level().getBlockEntity(chest) : null;
        for (Ingredient ingredient : recipe.value().getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack taken = ItemStack.EMPTY;
            if (container != null) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack slot = container.getItem(i);
                    if (!slot.isEmpty() && ingredient.test(slot)) {
                        taken = container.removeItem(i, 1);
                        break;
                    }
                }
            } else {
                // Capability inventory - pull one matching item through the handler.
                taken = Services.PLATFORM.extractMatching(golem.level(), chest, ingredient::test, 1, false);
            }
            if (taken.isEmpty()) {
                return false;
            }
            Item remainder = taken.getItem().getCraftingRemainingItem();
            if (remainder != null) {
                golem.depositToChest(new ItemStack(remainder));
            }
        }
        return true;
    }

    @Override
    public boolean canUse() {
        if (!golem.getMainHandItem().isEmpty() || !golem.hasMenu() || !golem.hasDepositChest()
                || !ContainerHelper.isContainer(golem, golem.getSupplyPos())) {
            return false;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }
        scanCooldown = SCAN_COOLDOWN;
        kitchenPos = golem.findKitchen();
        if (kitchenPos == null) {
            return false;
        }
        plan = findCookable();
        return plan != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !done && plan != null && kitchenPos != null
                && ContainerHelper.isContainer(golem, golem.getSupplyPos());
    }

    @Override
    public void start() {
        gathered = false;
        done = false;
        craftTicks = 0;
        moveTo(golem.getSupplyPos());
    }

    @Override
    public void stop() {
        plan = null;
        kitchenPos = null;
        gathered = false;
        done = false;
        craftTicks = 0;
        golem.getNavigation().stop();
    }

    private void moveTo(BlockPos pos) {
        golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Golem.defaultWalkSpeed);
    }

    @Override
    public void tick() {
        if (plan == null || kitchenPos == null) {
            return;
        }
        if (!gathered) {
            BlockPos chest = golem.getSupplyPos();
            golem.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
            // NOT ReachHelper (1.5 blocks): a golem stood beside a SOLID chest
            // sits ~2.6 away, so it could walk up, stop, and never be "in reach"
            // - it needed a physical shove to gather. Use the same forgiving
            // working distance the crafting table already gets.
            if (chest.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > WORK_REACH_SQ) {
                if (golem.getNavigation().isDone()) {
                    moveTo(chest);
                }
                return;
            }
            if (!consumeIngredients(plan)) {
                done = true;
                return;
            }
            gathered = true;
            moveTo(kitchenPos);
            return;
        }
        golem.getLookControl().setLookAt(kitchenPos.getX() + 0.5, kitchenPos.getY() + 0.5, kitchenPos.getZ() + 0.5);
        if (!ReachHelper.canReach(golem, kitchenPos)
                && kitchenPos.distToCenterSqr(golem.getX(), golem.getY(), golem.getZ()) > 9.0) {
            if (golem.getNavigation().isDone()) {
                moveTo(kitchenPos);
            }
            return;
        }
        if (++craftTicks < CRAFT_TICKS) {
            return;
        }
        ItemStack dish = plan.value().getResultItem(golem.level().registryAccess()).copy();
        golem.setItemSlot(EquipmentSlot.MAINHAND, dish);
        golem.level().playSound(null, kitchenPos, SoundEvents.VILLAGER_WORK_BUTCHER,
                SoundSource.NEUTRAL, 0.7F, 1.1F);
        done = true;
    }
}
