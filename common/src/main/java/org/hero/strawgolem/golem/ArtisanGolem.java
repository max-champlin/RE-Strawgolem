package org.hero.strawgolem.golem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.item.GolemRetrainerItem;

/**
 * The Cook's worldly counterpart: the same crafting machinery, but it will make
 * ESSENCE, not supper. Sneak-click it with an item to add that item to its
 * work order, bind it a supply chest, and stand a crafting table nearby - it
 * gathers the essence, crafts, and returns the finished product to the chest.
 * It will ONLY use recipes made entirely of Mystical Agriculture essences, so
 * it can never quietly eat your nuggets, ingots or storage blocks instead.
 */
public class ArtisanGolem extends CookGolem {

    /** Every Mystical Agriculture essence, 136 of them, in one tag. */
    private static final net.minecraft.tags.TagKey<Item> ESSENCES = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "essences"));

    public ArtisanGolem(EntityType<? extends StrawGolem> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    /**
     * Essence work only. An iron ingot has several recipes - from essence, from
     * nuggets, from a block - and left to itself the golem would happily eat
     * whichever the supply chest happened to hold. Restricting it to recipes
     * whose ingredients are ALL essences means it can only ever convert essence,
     * so nothing else in the chest is at risk.
     */
    @Override
    public boolean acceptsRecipe(net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> holder) {
        boolean sawIngredient = false;
        for (net.minecraft.world.item.crafting.Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            sawIngredient = true;
            for (ItemStack candidate : ingredient.getItems()) {
                if (!candidate.is(ESSENCES)) {
                    return false;
                }
            }
        }
        return sawIngredient;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // Same gesture as the Cook, minus the "must be food" gate - an artisan
        // takes any order. Handled here so the Cook's food check never runs.
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
