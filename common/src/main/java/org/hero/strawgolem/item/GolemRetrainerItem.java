package org.hero.strawgolem.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.hero.strawgolem.golem.BreederGolem;
import org.hero.strawgolem.golem.StockGolem;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.registry.EntityRegistry;

/**
 * Retrains a golem to its next profession (Harvester -> Breeder -> Stocker ->
 * Harvester) by replacing it with a fresh entity of the next type. Memories
 * (bound chests, carry filters) are wiped; position and health carry over.
 */
public class GolemRetrainerItem extends Item {

    public GolemRetrainerItem(Properties properties) {
        super(properties);
    }

    /**
     * Replaces a golem with a fresh one of the given profession at the same spot
     * and health. Memories (bindings, filters, menus) are wiped by design.
     */
    public static StrawGolem convert(StrawGolem golem, EntityType<? extends StrawGolem> next, net.minecraft.world.level.Level level) {
        StrawGolem fresh = next.create(level);
        if (fresh == null) {
            return null;
        }
        fresh.moveTo(golem.getX(), golem.getY(), golem.getZ(), golem.getYRot(), golem.getXRot());
        fresh.setHealth(Math.min(golem.getHealth(), fresh.getMaxHealth()));
        // Memories are wiped; muscle memory, souls, home, hats, and names are not.
        fresh.setJobsDone(golem.getJobsDone());
        if (golem.isImmortal()) {
            fresh.setImmortal(true);
        }
        fresh.setHomePos(golem.getHomePos());
        fresh.setHat(golem.hasHat());
        golem.getOwnerUUID().ifPresent(fresh::setOwnerUUID);
        // Carry the birth name across. This is THE line that keeps a golem's
        // identity stable - convert() mints a new entity (and a new UUID) for
        // retrains, hat swaps, stick refreshes and self-heals alike, so without
        // it a golem would be renamed several times over its working life.
        // The surname rides along even on a trade change, because a surname is
        // inherited rather than earned: Gerald Shafto who retrains as a Cook is
        // still Gerald Shafto.
        fresh.setBirthName(golem.getBirthName());
        if (golem.hasCustomName()) {
            fresh.setCustomName(golem.getCustomName());
            fresh.setCustomNameVisible(golem.isCustomNameVisible());
        }
        // Same-profession rebuild (refresh / self-heal): keep trained filters
        // too - a Cook shouldn't forget its menu just because it got rebuilt.
        // A real profession CHANGE (retrain) still wipes them, by design.
        if (next == golem.getType()) {
            if (golem instanceof org.hero.strawgolem.golem.CookGolem oldCook
                    && fresh instanceof org.hero.strawgolem.golem.CookGolem newCook) {
                newCook.replaceMenu(new java.util.ArrayList<>(oldCook.getMenu()));
            } else if (golem instanceof StockGolem oldStock && fresh instanceof StockGolem newStock) {
                newStock.replaceCarryFilter(new java.util.ArrayList<>(oldStock.getCarryFilter()));
            } else if (golem instanceof org.hero.strawgolem.golem.MinerGolem oldMiner
                    && fresh instanceof org.hero.strawgolem.golem.MinerGolem newMiner) {
                newMiner.replaceMineFilter(new java.util.ArrayList<>(oldMiner.getMineFilter()));
            }
        }
        golem.markExpectedRemoval(); // retrain/refresh rebuild - deliberate, don't log it as a loss
        golem.discard();
        level.addFreshEntity(fresh);
        return fresh;
    }

    private static final String MODE_KEY = "ForemanMode"; // 0=Retrainer, 1=Ledger, 2=Refresh
    private static final String KIND_KEY = "LedgerKind";
    private static final String IDS_KEY = "LedgerIds";
    private static final int MODE_RETRAINER = 0, MODE_LEDGER = 1, MODE_REFRESH = 2;

    static int getMode(ItemStack stack) {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? MODE_RETRAINER : data.copyTag().getInt(MODE_KEY);
    }

    static boolean isLedgerMode(ItemStack stack) {
        return getMode(stack) == MODE_LEDGER;
    }

    static boolean isRefreshMode(ItemStack stack) {
        return getMode(stack) == MODE_REFRESH;
    }

    /** Right-clicking the air cycles the stick: Retrainer -> Ledger -> Refresh. */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int mode = (getMode(stack) + 1) % 3;
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putInt(MODE_KEY, mode));
            String key = mode == MODE_LEDGER ? "strawgolem.mode.ledger"
                    : mode == MODE_REFRESH ? "strawgolem.mode.refresh"
                    : "strawgolem.mode.retrainer";
            player.displayClientMessage(Component.translatable(key), true);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Refresh mode: rebuild a golem as the SAME profession with clean AI state.
     * Fixes a corrupted/frozen golem (broken navigation baked into its save
     * data that a relog won't clear) in one click. Keeps rank, soul, home, and
     * hat; wipes trained filters/bindings (which is exactly what clears the bad
     * state - re-teach with the ledger if needed).
     */
    @SuppressWarnings("unchecked")
    private InteractionResult handleRefresh(Player player, StrawGolem golem) {
        StrawGolem fresh = convert(golem, (EntityType<? extends StrawGolem>) golem.getType(), player.level());
        if (fresh == null) {
            return InteractionResult.PASS;
        }
        player.level().playSound(null, fresh.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.NEUTRAL, 0.6F, 1.0F);
        player.displayClientMessage(Component.translatable("strawgolem.refreshed", fresh.getName()), true);
        return InteractionResult.SUCCESS;
    }

    /** Ledger mode: sneak-click copies a golem's training; click teaches it. */
    private InteractionResult handleLedger(ItemStack stack, Player player, StrawGolem golem) {
        String kind;
        java.util.Set<net.minecraft.world.item.Item> itemList = null;
        java.util.Set<net.minecraft.world.level.block.Block> blockList = null;
        if (golem instanceof org.hero.strawgolem.golem.CookGolem cook) {
            kind = "cook";
            itemList = cook.getMenu();
        } else if (golem instanceof org.hero.strawgolem.golem.ExcavatorGolem digger) {
            kind = "dig";
            blockList = digger.getMineFilter();
        } else if (golem instanceof org.hero.strawgolem.golem.MinerGolem miner) {
            kind = "mine";
            blockList = miner.getMineFilter();
        } else if (golem instanceof StockGolem stock) {
            kind = "carry";
            itemList = stock.getCarryFilter();
        } else {
            player.displayClientMessage(Component.translatable("strawgolem.ledger.untrainable", golem.getName()), true);
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            if (itemList != null) {
                for (net.minecraft.world.item.Item item : itemList) {
                    ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
                }
            } else {
                for (net.minecraft.world.level.block.Block block : blockList) {
                    ids.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
            if (ids.isEmpty()) {
                player.displayClientMessage(Component.translatable("strawgolem.ledger.nothing", golem.getName()), true);
                return InteractionResult.SUCCESS;
            }
            final String copiedKind = kind;
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putString(KIND_KEY, copiedKind);
                net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
                for (String id : ids) {
                    list.add(net.minecraft.nbt.StringTag.valueOf(id));
                }
                tag.put(IDS_KEY, list);
            });
            player.level().playSound(null, golem.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("strawgolem.ledger.copied", ids.size(), golem.getName()), true);
            return InteractionResult.SUCCESS;
        }

        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        String storedKind = tag.getString(KIND_KEY);
        if (storedKind.isEmpty()) {
            player.displayClientMessage(Component.translatable("strawgolem.ledger.empty"), true);
            return InteractionResult.SUCCESS;
        }
        if (!storedKind.equals(kind)) {
            player.displayClientMessage(Component.translatable("strawgolem.ledger.mismatch",
                    Component.translatable("strawgolem.ledger.kind." + storedKind)), true);
            return InteractionResult.SUCCESS;
        }
        net.minecraft.nbt.ListTag list = tag.getList(IDS_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        int taught;
        if (itemList != null) {
            java.util.List<net.minecraft.world.item.Item> items = new java.util.ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(list.getString(i));
                if (id != null) {
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
                }
            }
            taught = items.size();
            if (golem instanceof org.hero.strawgolem.golem.CookGolem cook) {
                cook.replaceMenu(items);
            } else if (golem instanceof StockGolem stock) {
                stock.replaceCarryFilter(items);
            }
        } else {
            java.util.List<net.minecraft.world.level.block.Block> blocks = new java.util.ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(list.getString(i));
                if (id != null) {
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
                }
            }
            taught = blocks.size();
            ((org.hero.strawgolem.golem.MinerGolem) golem).replaceMineFilter(blocks);
        }
        player.level().playSound(null, golem.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.NEUTRAL, 0.5F, 1.8F);
        player.displayClientMessage(Component.translatable("strawgolem.ledger.taught", taught, golem.getName()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        int mode = getMode(stack);
        tooltip.add(Component.translatable(mode == MODE_LEDGER
                ? "item.strawgolem.golem_retrainer.mode_ledger"
                : mode == MODE_REFRESH
                ? "item.strawgolem.golem_retrainer.mode_refresh"
                : "item.strawgolem.golem_retrainer.mode_retrainer")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        net.minecraft.world.item.component.CustomData pageData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (pageData != null) {
            net.minecraft.nbt.CompoundTag pageTag = pageData.copyTag();
            String pageKind = pageTag.getString(KIND_KEY);
            if (!pageKind.isEmpty()) {
                int n = pageTag.getList(IDS_KEY, net.minecraft.nbt.Tag.TAG_STRING).size();
                tooltip.add(Component.translatable("item.strawgolem.golem_retrainer.ledger_page",
                        Component.translatable("strawgolem.ledger.kind." + pageKind), n)
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("item.strawgolem.golem_retrainer.tooltip")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.strawgolem.golem_retrainer.tooltip3")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.strawgolem.golem_retrainer.tooltip2")
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
        if (isRefreshMode(stack)) {
            return handleRefresh(player, golem);
        }
        if (isLedgerMode(stack)) {
            return handleLedger(stack, player, golem);
        }

        // Cycle: Harvester -> Breeder -> Stocker -> Miner -> Excavator -> Beekeeper -> Fisher
        // -> Lumberjack -> Smelter -> Cook -> Harvester (sneak reverses).
        boolean reverse = player.isShiftKeyDown();
        EntityType<? extends StrawGolem> next;
        if (golem instanceof org.hero.strawgolem.golem.MetalworkerGolem) {
            next = reverse ? EntityRegistry.ARTISANGOLEM.get() : EntityRegistry.STRAWGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.ArtisanGolem) {
            next = reverse ? EntityRegistry.BUTCHERGOLEM.get() : EntityRegistry.METALWORKERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.ButcherGolem) {
            next = reverse ? EntityRegistry.BREWERGOLEM.get() : EntityRegistry.ARTISANGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.BrewerGolem) {
            next = reverse ? EntityRegistry.GARDENERGOLEM.get() : EntityRegistry.BUTCHERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.GardenerGolem) {
            next = reverse ? EntityRegistry.MILKMAIDGOLEM.get() : EntityRegistry.BREWERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.MilkmaidGolem) {
            next = reverse ? EntityRegistry.JANITORGOLEM.get() : EntityRegistry.GARDENERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.JanitorGolem) {
            next = reverse ? EntityRegistry.COOKGOLEM.get() : EntityRegistry.MILKMAIDGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.CookGolem) {
            next = reverse ? EntityRegistry.SMELTERGOLEM.get() : EntityRegistry.JANITORGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.SmelterGolem) {
            next = reverse ? EntityRegistry.LUMBERJACKGOLEM.get() : EntityRegistry.COOKGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.LumberjackGolem) {
            next = reverse ? EntityRegistry.FISHERGOLEM.get() : EntityRegistry.SMELTERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.FisherGolem) {
            next = reverse ? EntityRegistry.BEEKEEPERGOLEM.get() : EntityRegistry.LUMBERJACKGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.BeekeeperGolem) {
            next = reverse ? EntityRegistry.EXCAVATORGOLEM.get() : EntityRegistry.FISHERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.ExcavatorGolem) {
            next = reverse ? EntityRegistry.MINERGOLEM.get() : EntityRegistry.BEEKEEPERGOLEM.get();
        } else if (golem instanceof org.hero.strawgolem.golem.MinerGolem) {
            next = reverse ? EntityRegistry.STOCKGOLEM.get() : EntityRegistry.EXCAVATORGOLEM.get();
        } else if (golem instanceof StockGolem) {
            next = reverse ? EntityRegistry.BREEDERGOLEM.get() : EntityRegistry.MINERGOLEM.get();
        } else if (golem instanceof BreederGolem) {
            next = reverse ? EntityRegistry.STRAWGOLEM.get() : EntityRegistry.STOCKGOLEM.get();
        } else {
            next = reverse ? EntityRegistry.BREWERGOLEM.get() : EntityRegistry.BREEDERGOLEM.get();
        }

        StrawGolem fresh = convert(golem, next, player.level());
        if (fresh == null) {
            return InteractionResult.PASS;
        }

        player.level().playSound(null, fresh.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.NEUTRAL, 0.5F, 1.4F);
        player.displayClientMessage(Component.translatable("strawgolem.retrain", fresh.getName()), true);
        return InteractionResult.SUCCESS;
    }
}
