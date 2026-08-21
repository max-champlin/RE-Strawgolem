package org.hero.strawgolem.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Foreman's Clipboard: opens the Employee Directory, a roster of every
 * golem in range with trade, rank, hunger and what they're up to. Read-only -
 * everything it shows is already synched to the client, so it opens instantly
 * with no server round-trip.
 */
public class GolemDirectoryItem extends Item {

    public GolemDirectoryItem(Properties properties) {
        super(properties);
    }

    /** How far the locator and the server headcount reach. */
    public static final double LOCATE_RANGE = 64.0;
    /** Glow duration, in ticks. */
    private static final int LOCATE_TICKS = 300;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Sneak: roll call. Every golem in range lights up through the walls for
        // fifteen seconds, so a wanderer is easy to spot.
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int found = 0;
                for (org.hero.strawgolem.golem.StrawGolem golem : level.getEntitiesOfClass(
                        org.hero.strawgolem.golem.StrawGolem.class,
                        player.getBoundingBox().inflate(LOCATE_RANGE))) {
                    golem.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.GLOWING, LOCATE_TICKS, 0, false, false));
                    found++;
                }
                player.displayClientMessage(Component.translatable("strawgolem.directory.located", found), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        // Server side: stamp an authoritative headcount onto the stack itself.
        //
        // The screen reads client-side entities, which is what makes it instant -
        // but a client can hold ghost copies of entities the server already
        // removed. That happened for real: after a power cut ate 15 golems, the
        // directory kept listing all fifteen for a full day because no removal
        // packets ever arrived. It reported a crew that no longer existed.
        //
        // Item components sync to the holder automatically, so writing the count
        // here gets it to the client with no packet, no menu and no networking -
        // the same trick the Foreman's Stick uses for its ledger. The screen
        // re-reads it every frame, so it lands a tick after opening and any
        // mismatch with the client's own count is then visible.
        if (!level.isClientSide) {
            stampServerCount(level, player, stack);
        }
        if (level.isClientSide) {
            openDirectory();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Component key holding {@code count:gameTime} from the last server check. */
    public static final String COUNT_KEY = "ForemanHeadcount";

    private static void stampServerCount(Level level, Player player, ItemStack stack) {
        int count = level.getEntitiesOfClass(
                org.hero.strawgolem.golem.StrawGolem.class,
                player.getBoundingBox().inflate(LOCATE_RANGE)).size();
        // Sleeping golems are NOT entities - check-in discards the entity and
        // stores it as NBT in the bunkhouse. Counting only entities makes a
        // perfectly healthy crew read as "server sees 0" the moment night falls,
        // which is worse than no warning at all. Add the sleepers.
        count += sleepersNear(level, player);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString(COUNT_KEY, count + ":" + level.getGameTime());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
    }

    /**
     * Golems asleep in bunkhouses within range.
     *
     * <p>Scans block entities by chunk rather than probing every block position -
     * the search cube at 64 blocks is a third of a million positions, and this
     * runs on a right-click.
     */
    private static int sleepersNear(Level level, Player player) {
        int total = 0;
        int r = (int) LOCATE_RANGE;
        net.minecraft.core.BlockPos at = player.blockPosition();
        int minCx = (at.getX() - r) >> 4, maxCx = (at.getX() + r) >> 4;
        int minCz = (at.getZ() - r) >> 4, maxCz = (at.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity be
                        : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof org.hero.strawgolem.block.BunkhouseBlockEntity house
                            && house.getBlockPos().closerThan(at, LOCATE_RANGE)) {
                        total += house.occupants();
                    }
                }
            }
        }
        return total;
    }

    /**
     * Split out so the screen class is only touched on a client branch - keeps
     * a dedicated server from ever loading client-only classes.
     */
    private static void openDirectory() {
        org.hero.strawgolem.client.GolemDirectoryScreen.open();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("item.strawgolem.golem_directory.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.golem_directory.tooltip2")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
    }
}
