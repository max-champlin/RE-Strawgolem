package org.hero.strawgolem.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * One death undone, at the price a death is worth.
 *
 * <p>Spent by sneak-clicking a lodge. The most recent golem to die there gets
 * up again - genuinely the same one, same UUID, same name, same tally of work -
 * with its lifespan restarted, its belly half empty, and no more protection
 * from time than it had before.
 *
 * <p><b>The cost is deliberate and is not a mistake.</b> One Promise is nine
 * nether stars, 1,458 diamonds, 13,122 redstone and 2,187 netherite ingots -
 * roughly 8,748 ancient debris. That was calculated, shown to the author, and
 * kept on purpose. Equivalent exchange: a life is not a convenience item, and
 * this is the last door in a corridor of cheaper ones.
 *
 * <p>Everything upstream of here is nearly free by comparison. An Immortal Soul
 * is a single item and stops a Master ageing out at all. A lodge with
 * auto-retire on spends those souls by itself. Masters are now retired in the
 * field at the moment of death. Losses are announced in chat while you are
 * standing there. To need a Promise, all four of those had to be walked past.
 *
 * <p>So do not "balance" this downward without asking. Cheap resurrection makes
 * every safeguard above it pointless, and the point of the price is that you
 * would rather have not needed it.
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
