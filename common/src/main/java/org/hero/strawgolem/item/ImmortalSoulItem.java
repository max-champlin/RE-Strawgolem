package org.hero.strawgolem.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.golem.StrawGolem;

/**
 * Frees a golem from time: it will never age again - and never learn again.
 * Growth requires mortality; immortality is a retirement gift, best given at
 * the peak of a career. Does nothing against cows, creepers, or cliffs.
 */
public class ImmortalSoulItem extends Item {

    public ImmortalSoulItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.strawgolem.immortal_soul.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.immortal_soul.tooltip2")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof StrawGolem golem)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (golem.isImmortal()) {
            player.displayClientMessage(Component.translatable("strawgolem.soul.already"), true);
            return InteractionResult.SUCCESS;
        }
        golem.setImmortal(true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.level().playSound(null, golem.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.NEUTRAL, 0.6F, 1.4F);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    golem.getX(), golem.getY() + 0.6, golem.getZ(), 30, 0.3, 0.5, 0.3, 0.08);
        }
        player.displayClientMessage(Component.translatable("strawgolem.soul.applied", golem.getName()), true);
        return InteractionResult.SUCCESS;
    }
}
