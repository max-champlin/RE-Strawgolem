package org.hero.strawgolem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.List;

/**
 * Fits a golem with a cargo hold so it stops delivering one item at a time.
 *
 * <p>A harvester overwrites its main hand with a single drop and will not pick
 * again until that hand is empty, so every essence costs a full round trip to a
 * chest. At a chest every 20 blocks that walk is about 95% of the job, and a
 * 16-slot pack works out at roughly SIX TIMES the throughput - the difference
 * between two dozen golems on a 2000-block farm and four.
 *
 * <p>Merging into the held stack would have been cheaper and does not work:
 * Mystical Agriculture crops drop a seed AND an essence, so even a single-crop
 * plot mixes item types and a stack-merge stalls on the first mismatch. The
 * hold keeps sixteen KINDS of drop, which is what a mixed farm actually needs.
 *
 * <p>An upgrade rather than a default, in the Thaumcraft spirit: a golem you
 * have not fitted behaves exactly as it always did, so nothing already built
 * changes underneath you.
 */
public class GolemBackpackItem extends Item {

    public GolemBackpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof StrawGolem golem)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (golem.hasBackpack()) {
            player.displayClientMessage(
                    Component.translatable("strawgolem.backpack.already"), true);
            return InteractionResult.SUCCESS;
        }

        golem.setBackpack(true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.level().playSound(null, golem.blockPosition(),
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.NEUTRAL, 0.8f, 1.0f);
        player.displayClientMessage(
                Component.translatable("strawgolem.backpack.fitted",
                        golem.getName(), golem.satchelCapacity()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.strawgolem.golem_backpack.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.golem_backpack.flavor")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
