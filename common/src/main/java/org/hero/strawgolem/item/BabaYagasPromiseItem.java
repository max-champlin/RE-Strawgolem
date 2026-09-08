package org.hero.strawgolem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One death undone, at a price you feel.
 *
 * <p>Spent by sneak-clicking a lodge. The most recent golem to die there gets
 * up again - genuinely the same one, same UUID, same name, same tally of work -
 * with its lifespan restarted, its belly half empty, and no more protection
 * from time than it had before.
 *
 * <p>An Immortal Soul is one item and stops a Master ageing out at all. This is
 * four blocks of compressed wealth and a nether star, and only works on someone
 * already dead. That gap is the point: the cheap thing is the thing you should
 * have done, and the expensive thing is what it costs to have not done it.
 */
public class BabaYagasPromiseItem extends Item {

    public BabaYagasPromiseItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.strawgolem.baba_yagas_promise.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.baba_yagas_promise.tooltip2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.baba_yagas_promise.tooltip3")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.strawgolem.baba_yagas_promise.tooltip4")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
