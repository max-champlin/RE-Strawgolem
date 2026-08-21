package org.hero.strawgolem.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.golem.StrawGolem;

/**
 * A straw-lined sack that carries exactly ONE straw golem for relocating a base.
 * Stores the golem's FULL entity NBT, so rank, profession, soul, home, hat,
 * hunger, trained filters and name all survive the trip untouched. Only works
 * on straw golems - it will not pocket a cow. Right-click a golem to tuck it in;
 * right-click a block to set it back down. Reusable.
 */
public class GolemCarrierItem extends Item {
    private static final String GOLEM_KEY = "CarriedGolem";
    private static final String LABEL_KEY = "GolemLabel";

    public GolemCarrierItem(Properties properties) {
        super(properties);
    }

    public static boolean isFull(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(GOLEM_KEY);
    }

    /**
     * Sets the carried golem down at the given spot and empties the stack.
     * Shared by right-click release and the safety events (despawn/death). On
     * any failure the stack is left untouched so the golem is never lost.
     * Returns true only if a golem was actually placed.
     */
    public static boolean releaseInto(ItemStack stack, Level level, double x, double y, double z) {
        if (level.isClientSide || !isFull(stack)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data.copyTag().getCompound(GOLEM_KEY);
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
        if (entity == null) {
            Constants.LOG.error("Golem Carrier: carried golem failed to load - keeping it stored so it isn't lost.");
            return false;
        }
        entity.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        if (!level.addFreshEntity(entity)) {
            Constants.LOG.error("Golem Carrier: could not place the golem - keeping it stored.");
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.remove(GOLEM_KEY);
            t.remove(LABEL_KEY);
        });
        return true;
    }

    /** Right-click a golem: tuck it into the sack (only if empty). */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof StrawGolem golem)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (isFull(stack)) {
            player.displayClientMessage(Component.translatable("strawgolem.carrier.occupied"), true);
            return InteractionResult.SUCCESS;
        }
        golem.claimIfUnowned(player);
        CompoundTag tag = new CompoundTag();
        if (!golem.save(tag)) {
            player.displayClientMessage(Component.translatable("strawgolem.carrier.failed"), true);
            return InteractionResult.SUCCESS;
        }
        tag.remove("Passengers");
        // Fail-safe: prove the golem can be rebuilt from what we just saved
        // BEFORE we discard the real one, so a golem is never lost to a bad
        // round-trip (same guarantee the bunkhouse uses).
        if (EntityType.loadEntityRecursive(tag, player.level(), e -> e) == null) {
            Constants.LOG.error("Golem Carrier: capture aborted - golem NBT failed a reload test, leaving it in the world.");
            player.displayClientMessage(Component.translatable("strawgolem.carrier.failed"), true);
            return InteractionResult.SUCCESS;
        }
        String label = golem.getName().getString();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.put(GOLEM_KEY, tag);
            t.putString(LABEL_KEY, label);
        });
        golem.updateRoster(org.hero.strawgolem.network.RosterEntry.STATE_STOWED);
        golem.markExpectedRemoval(); // bindle capture - deliberate, don't log it as a loss
        golem.discard();
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        player.displayClientMessage(Component.translatable("strawgolem.carrier.captured", label), true);
        return InteractionResult.SUCCESS;
    }

    /** Right-click a block: set the carried golem back down. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (!isFull(stack)) {
            return InteractionResult.PASS;
        }
        Level level = ctx.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (releaseInto(stack, level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.9F);
        } else if (ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("strawgolem.carrier.failed"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        if (isFull(stack)) {
            String label = stack.get(DataComponents.CUSTOM_DATA).copyTag().getString(LABEL_KEY);
            tooltip.add(Component.translatable("item.strawgolem.golem_carrier.holding", label)
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
            tooltip.add(Component.translatable("item.strawgolem.golem_carrier.tooltip_full")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.strawgolem.golem_carrier.tooltip_empty")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.strawgolem.golem_carrier.flavor")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }

    /** Show the "full" bar (like a bundle) so a loaded sack reads at a glance. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return isFull(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return isFull(stack) ? 13 : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x59C64A; // straw-green
    }
}
