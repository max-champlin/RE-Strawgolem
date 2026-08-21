package org.hero.strawgolem.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.golem.api.ContainerHelper;

/**
 * The Traffic Cone: routes a golem's pickup and drop-off to different chests.
 *
 * <p>Every golem used to have a single bound chest that served as BOTH the box
 * it takes from and the box it puts into. That is fine for a harvester, and
 * quietly broken for anything that crafts: feed a crafting golem faster than it
 * works and its one chest fills up, leaving it holding finished goods with
 * nowhere to put them.
 *
 * <p>Three gestures, deliberately mirroring the Foreman's Stick:
 * <ul>
 *   <li><b>Right-click air</b> - swap between Pick-up and Drop-off mode.</li>
 *   <li><b>Right-click a container</b> - note that chest down on the cone.</li>
 *   <li><b>Right-click a golem</b> - assign the noted chest to that golem, in
 *       whichever mode the cone is set to.</li>
 * </ul>
 *
 * <p>Sneak-clicking a golem clears its pickup, putting it back to using one
 * chest for both jobs - which is exactly how every existing golem behaves,
 * since an unset pickup falls through to the drop-off position.
 */
public class TrafficConeItem extends Item {
    private static final String MODE_KEY = "ConeMode";   // 0 = pickup, 1 = dropoff
    private static final String POS_KEY = "ConePos";

    public TrafficConeItem(Properties properties) {
        super(properties);
    }

    private static boolean isDropoff(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getInt(MODE_KEY) == 1;
    }

    private static BlockPos notedPos(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().contains(POS_KEY)) {
            return null;
        }
        return BlockPos.of(data.copyTag().getLong(POS_KEY));
    }

    /** Right-click air: flip the mode. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean toDropoff = !isDropoff(stack);
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    t -> t.putInt(MODE_KEY, toDropoff ? 1 : 0));
            player.displayClientMessage(Component.translatable(
                    toDropoff ? "strawgolem.cone.mode_dropoff" : "strawgolem.cone.mode_pickup"), true);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.PLAYERS, 0.7F, toDropoff ? 1.2F : 0.9F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Right-click a container: note it down. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = ctx.getPlayer();
        if (!ContainerHelper.isContainer(level, pos)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("strawgolem.cone.not_container"), true);
            }
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = ctx.getItemInHand();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putLong(POS_KEY, pos.asLong()));
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    isDropoff(stack) ? "strawgolem.cone.noted_dropoff" : "strawgolem.cone.noted_pickup",
                    pos.toShortString()), true);
        }
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /** Right-click a golem: hand it the route. Sneak-click clears its pickup. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof StrawGolem golem)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            golem.setPickupPos(null);
            player.displayClientMessage(Component.translatable("strawgolem.cone.cleared"), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = notedPos(stack);
        if (pos == null) {
            player.displayClientMessage(Component.translatable("strawgolem.cone.nothing_noted"), true);
            return InteractionResult.SUCCESS;
        }
        golem.claimIfUnowned(player);
        if (isDropoff(stack)) {
            golem.setPriorityPos(pos);
            player.displayClientMessage(Component.translatable("strawgolem.cone.set_dropoff",
                    golem.getName().getString(), pos.toShortString()), true);
        } else {
            golem.setPickupPos(pos);
            player.displayClientMessage(Component.translatable("strawgolem.cone.set_pickup",
                    golem.getName().getString(), pos.toShortString()), true);
        }
        player.level().playSound(null, golem.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable(isDropoff(stack)
                        ? "strawgolem.cone.mode_dropoff" : "strawgolem.cone.mode_pickup")
                .withStyle(isDropoff(stack) ? net.minecraft.ChatFormatting.GOLD
                        : net.minecraft.ChatFormatting.AQUA));
        BlockPos pos = notedPos(stack);
        tooltip.add(Component.translatable(pos == null ? "strawgolem.cone.nothing_noted"
                        : "strawgolem.cone.noted_at", pos == null ? "" : pos.toShortString())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.traffic_cone.flavor")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }
}
