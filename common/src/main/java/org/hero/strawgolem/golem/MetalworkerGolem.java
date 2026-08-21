package org.hero.strawgolem.golem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.item.GolemRetrainerItem;

import java.util.List;

/**
 * The Artisan's opposite number: it only does metalwork - nuggets into ingots,
 * ingots into blocks, and back down again. Deliberately blind to essence
 * recipes so it can never compete with the Artisan over the same output; the
 * two can share a base without ever reaching into each other's supplies.
 */
public class MetalworkerGolem extends CookGolem {

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    /** The compressible families: nuggets, ingots, gems, raw ore and blocks. */
    private static final List<TagKey<Item>> METAL_TAGS = List.of(
            tag("nuggets"), tag("ingots"), tag("gems"), tag("raw_materials"), tag("storage_blocks"));

    public MetalworkerGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    private static boolean isMetal(ItemStack stack) {
        for (TagKey<Item> tag : METAL_TAGS) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only recipes built entirely from nuggets / ingots / gems / raw materials /
     * storage blocks. That covers the whole compression ladder in both
     * directions and, crucially, excludes every essence recipe - so this golem
     * and the Artisan can never fight over the same ingot.
     */
    @Override
    public boolean acceptsRecipe(RecipeHolder<CraftingRecipe> holder) {
        boolean sawIngredient = false;
        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            sawIngredient = true;
            for (ItemStack candidate : ingredient.getItems()) {
                if (!isMetal(candidate)) {
                    return false;
                }
            }
        }
        return sawIngredient;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // Same work-order gesture as the Artisan; no food gate.
        if (player.isShiftKeyDown() && !held.isEmpty() && !(held.getItem() instanceof GolemRetrainerItem)) {
            if (!level().isClientSide) {
                Item item = held.getItem();
                if (getMenu().remove(item)) {
                    player.displayClientMessage(Component.translatable(
                            getMenu().isEmpty() ? "strawgolem.workorder.cleared" : "strawgolem.workorder.removed",
                            held.getHoverName()), true);
                } else {
                    getMenu().add(item);
                    player.displayClientMessage(Component.translatable(
                            "strawgolem.workorder.added", held.getHoverName()), true);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }
}
