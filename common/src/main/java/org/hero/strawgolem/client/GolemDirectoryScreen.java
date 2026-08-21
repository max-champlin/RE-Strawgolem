package org.hero.strawgolem.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.hero.strawgolem.Constants;
import org.hero.strawgolem.golem.StrawGolem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Employee Directory: a read-only roster of every golem the client can see.
 * Everything shown (rank, hunger, soul, carry status) is synched entity data, so
 * this needs no networking at all - we just read the golems already loaded.
 * Drawn with primitives so it needs no custom artwork.
 */
public class GolemDirectoryScreen extends Screen {
    private static final int PANEL_W = 440;
    private static final int PANEL_H = 224;
    private static final int ROW_H = 12;
    private static final int HEADER_H = 58;
    /**
     * Space kept clear at the bottom for the scroll counter and the tip.
     *
     * <p>34, not 20, because the tips run 78-144 characters and a single line
     * holds about 54 once the counter is reserved - so every one of them was
     * being cut, the longest losing two thirds of itself mid-word. Two wrapped
     * lines fit them all. PANEL_H grew by the same 14 so the roster still shows
     * eleven rows.
     */
    private static final int FOOTER_H = 34;

    // Column x-offsets from the panel's left edge.
    private static final int COL_NAME = 8;
    private static final int COL_RANK = 150;
    private static final int COL_TRADE = 215;
    private static final int COL_FED = 320;
    private static final int COL_STATUS = 372;

    /** Foreman's tips - one is picked each time the directory is opened. */
    private static final int TIP_COUNT = 5;

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> byTrade = new LinkedHashMap<>();
    private int tip = 0;
    private int scroll = 0;
    private int hungryCount = 0;
    private int haulingCount = 0;
    /** Lamp hit-box and verdict, recorded during render for the hover tooltip. */
    private int hoverLampX = -1;
    private int hoverLampY = -1;
    private Health hoverHealth = null;
    private int immortalCount = 0;
    /** UUIDs we already listed from local entities/bindles, so remote rows don't duplicate them. */
    private final java.util.Set<java.util.UUID> localIds = new java.util.HashSet<>();
    /** Last roster version we rendered, so we repopulate when a response arrives. */
    private int rosterVersion = -1;

    /**
     * Which golem's detail page is open, or null for the roster list.
     *
     * <p>Held as a UUID rather than a Row so the page survives a rebuild - the
     * roster refreshes whenever the server answers, and holding the record
     * itself would leave the page showing a stale snapshot.
     */
    private java.util.UUID selected = null;

    /** Legend overlay, toggled from the list header. */
    private boolean legend = false;

    /** Hit-boxes recorded during render, so clicks land on what was drawn. */
    private int backX, backY, backW, backH;
    private int legendX, legendY, legendW, legendH;

    /**
     * One line of the roster, plus everything the detail page shows.
     *
     * <p>The extra fields are filled from the live entity where we have one and
     * left at defaults for golems we only know about second-hand (asleep in a
     * bunkhouse, stowed in someone's bindle). That is deliberate: reading the
     * entity client-side costs nothing, whereas widening RosterEntry would mean
     * a network change for data that is only ever looked at one golem at a time.
     */
    private record Row(String name, boolean named, String trade, String assigned, int rank,
                       boolean immortal, int hungerPct, String status, String where,
                       boolean remote,
                       java.util.UUID id, int jobs, boolean backpack,
                       int bagUsed, int bagCap, int lifePct,
                       net.minecraft.core.BlockPos home,
                       net.minecraft.core.BlockPos chest) {}

    /** Detail defaults for a golem we cannot see directly. */
    private static Row remoteRow(String name, boolean named, String trade, int rank,
                                 boolean immortal, int hungerPct, String status,
                                 String where, java.util.UUID id) {
        return new Row(name, named, trade, "", rank, immortal, hungerPct, status, where,
                true, id, -1, false, -1, -1, -1, null, null);
    }

    public GolemDirectoryScreen() {
        super(Component.translatable("screen.strawgolem.directory"));
    }

    /** Opens the directory. Client-only entry point. */
    public static void open() {
        Minecraft.getInstance().setScreen(new GolemDirectoryScreen());
    }

    @Override
    protected void init() {
        tip = (int) (Math.random() * TIP_COUNT);
        // Ask the server for the FULL roster - golems that are unloaded, asleep
        // in a bunkhouse, or in someone else's bindle have no entity here, so
        // the local scan below can never see them. The screen opens instantly
        // off local entities and fills in the rest when the answer lands.
        org.hero.strawgolem.platform.Services.PLATFORM.requestRoster();
        rebuild();
    }

    /** (Re)builds every row: local entities, bindles in your pack, then the server's roster. */
    private void rebuild() {
        rosterVersion = org.hero.strawgolem.network.RosterCache.version();
        localIds.clear();
        rows.clear();
        byTrade.clear();
        hungryCount = 0;
        haulingCount = 0;
        immortalCount = 0;
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        int maxHunger = Math.max(1, Constants.Golem.maxHunger);
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof StrawGolem golem) || !golem.isAlive()) {
                continue;
            }
            String trade = tradeOf(golem);
            byTrade.merge(trade, 1, Integer::sum);

            int hungerPct = Constants.Golem.hunger
                    ? Math.min(100, Math.round(golem.getHunger() * 100.0F / maxHunger))
                    : 0;
            boolean hauling = !golem.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            boolean hungry = Constants.Golem.hunger && hungerPct >= 60;
            if (hungry) {
                hungryCount++;
            }
            if (hauling) {
                haulingCount++;
            }
            if (golem.isImmortal()) {
                immortalCount++;
            }
            String status = golem.getPanic() ? "Panicking"
                    : hungry ? "Hungry"
                    : hauling ? "Hauling"
                    : "Working";
            // "named" now means YOU named it - every golem has a birth name, so
            // the flag is what still sorts your own hires to the top and styles
            // them apart from the ones the world christened.
            boolean named = golem.hasCustomName() && golem.getCustomName() != null;
            String name = named ? golem.getCustomName().getString()
                    : golem.getBirthName().isEmpty() ? "(unnamed)" : golem.getBirthName();
            String where = Math.round(minecraft.player == null ? 0 : minecraft.player.distanceTo(golem)) + "m";
            localIds.add(golem.getUUID());
            int cap = golem.satchelCapacity();
            int used = golem.getSatchel().size();
            int lifePct = (!golem.isImmortal() && Constants.Golem.lifespan && Constants.Golem.maxLife > 0)
                    ? Math.max(0, Math.min(100,
                        Math.round(golem.getLifeSpan() * 100.0F / Constants.Golem.maxLife)))
                    : -1;
            net.minecraft.core.BlockPos chest = golem.getPriorityPos();
            if (chest != null && chest.getX() == Integer.MAX_VALUE) {
                chest = null;
            }
            rows.add(new Row(name, named, trade, golem.getAssignmentLabel(), golem.getRank(),
                    golem.isImmortal(), hungerPct, status, where, false,
                    golem.getUUID(), golem.getJobsDone(), golem.hasBackpack(),
                    used, cap, lifePct, golem.getHomePos(), chest));
        }
        // Golems asleep in a bindle in your pack. Their full entity NBT sits in
        // the item, so listing them needs no networking - but note this only
        // sees bindles YOU are carrying.
        if (minecraft.player != null) {
            for (net.minecraft.world.item.ItemStack stack : minecraft.player.getInventory().items) {
                if (!(stack.getItem() instanceof org.hero.strawgolem.item.GolemCarrierItem)
                        || !org.hero.strawgolem.item.GolemCarrierItem.isFull(stack)) {
                    continue;
                }
                net.minecraft.world.item.component.CustomData data =
                        stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (data == null) {
                    continue;
                }
                net.minecraft.nbt.CompoundTag tag = data.copyTag().getCompound("CarriedGolem");
                String label = data.copyTag().getString("GolemLabel");
                int jobs = tag.getInt("jobsDone");
                int rank = jobs >= 200 ? 2 : jobs >= 50 ? 1 : 0;
                String type = tag.getString("id");
                if (type.contains(":")) {
                    type = type.substring(type.indexOf(':') + 1);
                }
                type = type.replace("_golem", "");
                if (type.equals("strawgolem") || type.isEmpty()) {
                    type = "Harvester";
                } else {
                    type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
                }
                if (tag.hasUUID("UUID")) {
                    localIds.add(tag.getUUID("UUID"));
                }
                String birth = tag.getString("birthName");
                String shown = !label.isEmpty() ? label : birth.isEmpty() ? "(unnamed)" : birth;
                // Bindled golems are stored NBT, so the pack/jobs data is right
                // there in the tag - no entity needed.
                rows.add(new Row(shown, !label.isEmpty(), type, "",
                        rank, tag.getBoolean("immortal"), 0, "In bindle", "", false,
                        tag.hasUUID("UUID") ? tag.getUUID("UUID") : null,
                        tag.getInt("jobsDone"), tag.getBoolean("backpack"),
                        -1, -1, -1, null, null));
                byTrade.merge(type, 1, Integer::sum);
            }
        }
        // Anything the server knows about that we could not see for ourselves.
        for (org.hero.strawgolem.network.RosterEntry e : org.hero.strawgolem.network.RosterCache.get()) {
            if (localIds.contains(e.golemId())) {
                continue;
            }
            String trade = tradeFromClass(e.trade());
            byTrade.merge(trade, 1, Integer::sum);
            if (e.immortal()) {
                immortalCount++;
            }
            String status = switch (e.state()) {
                // "Asleep" rather than "In bunkhouse" so the distance that gets
                // appended after it still fits the column - the long label was
                // being clipped to "In bunkhou" and taking the location with it.
                case org.hero.strawgolem.network.RosterEntry.STATE_ASLEEP -> "Asleep";
                case org.hero.strawgolem.network.RosterEntry.STATE_STOWED -> "In bindle";
                default -> "Away";
            };
            String where = describeRemote(e);
            int pct = Constants.Golem.hunger
                    ? Math.min(100, Math.round(e.hunger() * 100.0F / maxHunger)) : 0;
            rows.add(remoteRow(e.name().isEmpty() ? "(unnamed)" : e.name(), !e.name().isEmpty(),
                    trade, e.rank(), e.immortal(), pct, status, where, e.golemId()));
        }
        // Best workers first, then named before unnamed, then alphabetical.
        rows.sort(Comparator.comparingInt(Row::rank).reversed()
                .thenComparing(Row::named, Comparator.reverseOrder())
                .thenComparing(Row::name));
    }

    private int visibleRows() {
        return (PANEL_H - HEADER_H - FOOTER_H) / ROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    /**
     * Vanilla's renderBackground runs the menu BLUR shader over the world. For a
     * roster you glance at while standing in your base that just fogs everything
     * for no benefit - so use the plain dim instead, no blur.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The server's reply arrives a tick or two after the screen opens, so
        // fold it in as soon as the cache version moves. Cheap: an int compare
        // per frame, and a rebuild only on the frame the answer actually lands.
        if (rosterVersion != org.hero.strawgolem.network.RosterCache.version()) {
            rebuild();
        }
        // super.render draws the background (our no-blur override) AND widgets.
        // It MUST come first - calling it last paints the dim over the roster.
        super.render(g, mouseX, mouseY, partialTick);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;

        // Panel
        g.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, 0xFF2B2016);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xFF3E2F21);
        g.fill(left, top, left + PANEL_W, top + HEADER_H - 4, 0xFF4A3826);

        // Detail page and legend take over the whole panel. Checked here, after
        // the frame is drawn but before any roster chrome, so neither view has
        // to undo the list's header.
        if (selected != null) {
            Row picked = rowFor(selected);
            if (picked == null) {
                selected = null;      // golem died or wandered out of range
            } else {
                renderDetail(g, left, top, picked, mouseX, mouseY);
                return;
            }
        }
        if (legend) {
            renderLegend(g, left, top, mouseX, mouseY);
            return;
        }

        // Title + headline numbers
        g.drawString(font, Component.translatable("screen.strawgolem.directory")
                .withStyle(ChatFormatting.GOLD), left + 8, top + 7, 0xFFFFFF, false);
        // "?" opens the legend. Sits in the header rather than the footer so it
        // does not fight the Foreman tip for width - that reserve is already the
        // reason tips were being clipped mid-word.
        String q = "?";
        legendW = font.width(q) + 10;
        legendH = 12;
        legendX = left + PANEL_W - legendW - 8;
        legendY = top + 20;
        boolean overQ = mouseX >= legendX && mouseX <= legendX + legendW
                && mouseY >= legendY && mouseY <= legendY + legendH;
        g.fill(legendX, legendY, legendX + legendW, legendY + legendH,
                overQ ? 0x40FFFFFF : 0x20FFFFFF);
        g.drawString(font, q, legendX + 5, legendY + 2,
                overQ ? 0xFFFFFF : 0xC9B99F, false);

        String head = rows.size() + " on the books"
                + "  |  " + haulingCount + " hauling"
                + "  |  " + hungryCount + " hungry"
                + "  |  " + immortalCount + " timeless";
        g.drawString(font, head, left + 8, top + 19, 0xBFBFBF, false);

        // Status lamp - one glance tells you whether to care. The explanation
        // lives on hover so a healthy panel stays quiet instead of carrying a
        // line of text that is usually irrelevant.
        Health health = assess(readServerCount());
        int lampX = left + PANEL_W - 14;
        int lampY = top + 12;
        g.fill(lampX - 5, lampY - 5, lampX + 5, lampY + 5, 0xFF1C1C1C);
        g.fill(lampX - 4, lampY - 4, lampX + 4, lampY + 4, health.colour());
        // a single highlight pixel block reads as a lit bulb rather than a square
        g.fill(lampX - 3, lampY - 3, lampX - 1, lampY - 1, 0x66FFFFFF);
        g.drawString(font, health.label(),
                lampX - 9 - font.width(health.label()), top + 7, health.colour(), false);
        hoverLampX = lampX;
        hoverLampY = lampY;
        hoverHealth = health;

        // Trade breakdown (compact, truncated to fit)
        StringBuilder trades = new StringBuilder();
        for (Map.Entry<String, Integer> e : byTrade.entrySet()) {
            if (trades.length() > 0) {
                trades.append(", ");
            }
            trades.append(e.getValue()).append("x ").append(e.getKey());
        }
        String tradeLine = trades.length() == 0 ? "no golems in range" : trades.toString();
        g.drawString(font, font.plainSubstrByWidth(tradeLine, PANEL_W - 16),
                left + 8, top + 31, 0x7FD4C8, false);

        // Column headers
        int headerY = top + 45;
        g.drawString(font, "Name", left + COL_NAME, headerY, 0x9A8A78, false);
        g.drawString(font, "Rank", left + COL_RANK, headerY, 0x9A8A78, false);
        g.drawString(font, "Trade / Assigned", left + COL_TRADE, headerY, 0x9A8A78, false);
        if (Constants.Golem.hunger) {
            g.drawString(font, "Fed", left + COL_FED, headerY, 0x9A8A78, false);
        }
        g.drawString(font, "Status", left + COL_STATUS, headerY, 0x9A8A78, false);

        int y = top + HEADER_H;
        g.fill(left + 4, y - 4, left + PANEL_W - 4, y - 3, 0x40FFFFFF);
        if (rows.isEmpty()) {
            g.drawString(font, "No golems in range - stand nearer the crew.",
                    left + 8, y + 4, 0x808080, false);
        }

        int shown = visibleRows();
        for (int i = 0; i < shown && (i + scroll) < rows.size(); i++) {
            Row r = rows.get(i + scroll);
            int ry = y + 2 + i * ROW_H;
            // Highlight under the cursor: the rows are clickable now, and
            // nothing else on the panel says so.
            if (mouseX >= left && mouseX <= left + PANEL_W
                    && mouseY >= ry - 1 && mouseY < ry + ROW_H - 2) {
                g.fill(left + 4, ry - 1, left + PANEL_W - 4, ry + ROW_H - 2, 0x28FFD54F);
            }
            if (((i + scroll) & 1) == 0) {
                g.fill(left + 4, ry - 1, left + PANEL_W - 4, ry + ROW_H - 2, 0x18FFFFFF);
            }
            // Name: gold if timeless, muted if never named.
            int nameColor = r.immortal() ? 0xE6C46B : r.named() ? 0xE0E0E0 : 0x6E6257;
            g.drawString(font, font.plainSubstrByWidth(r.name(), COL_RANK - COL_NAME - 6),
                    left + COL_NAME, ry, nameColor, false);
            // Rank
            g.drawString(font, rankName(r.rank()), left + COL_RANK, ry, rankColor(r.rank()), false);
            // Trade
            // An assigned golem shows WHAT it's waiting on, so an idle
            // specialist doesn't just look like it's slacking off.
            boolean assigned = r.assigned() != null && !r.assigned().isEmpty();
            String tradeText = assigned ? r.assigned() : r.trade();
            g.drawString(font, font.plainSubstrByWidth(tradeText, COL_FED - COL_TRADE - 6),
                    left + COL_TRADE, ry, assigned ? 0xE8C06B : 0x9FC7E8, false);
            // Fed bar (full = well fed)
            if (Constants.Golem.hunger) {
                int barX = left + COL_FED;
                g.fill(barX, ry + 1, barX + 40, ry + 7, 0xFF1E1E1E);
                int fill = Math.round(40 * (100 - r.hungerPct()) / 100.0F);
                int color = r.hungerPct() >= 60 ? 0xFFCC5555 : r.hungerPct() >= 30 ? 0xFFCCAA55 : 0xFF66BB55;
                g.fill(barX, ry + 1, barX + fill, ry + 7, color);
            }
            int statusColor = switch (r.status()) {
                case "Panicking" -> 0xFF7777;
                case "Hungry" -> 0xCC8855;
                case "Hauling" -> 0x88CCFF;
                case "In bindle" -> 0xC9A0DC;
                case "Asleep" -> 0x9999CC;
                default -> 0x88BB88;
            };
            String statusText = r.where().isEmpty() ? r.status() : r.status() + " " + r.where();
            g.drawString(font, font.plainSubstrByWidth(statusText, PANEL_W - COL_STATUS - 8),
                    left + COL_STATUS, ry, statusColor, false);
        }

        // Foreman's tip along the bottom - the pinned notice on the break room wall.
        int tipY = top + PANEL_H - FOOTER_H + 4;
        g.fill(left + 4, tipY - 4, left + PANEL_W - 4, tipY - 3, 0x30FFFFFF);

        // Counter first, on its own line, so the tip below gets the full width.
        if (rows.size() > shown) {
            String more = (scroll + shown) + "/" + rows.size() + "  (scroll)";
            g.drawString(font, more, left + PANEL_W - font.width(more) - 8, tipY,
                    0x707070, false);
        }

        // WRAPPED, not truncated. font.split does the measuring, so this stays
        // correct if a tip is reworded or another one is added.
        String tipText = Component.translatable("screen.strawgolem.directory.tip." + tip)
                .getString();
        java.util.List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal("Foreman's tip: " + tipText), PANEL_W - 16);
        int ty = tipY + 11;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), left + 8, ty, 0x8A7A62, false);
            ty += 10;
        }
    }

    /**
     * "ArtisanGolem" -> "Artisan"; the base straw golem reads as "Harvester",
     * because it IS the harvester and its entity name doesn't say so.
     *
     * <p>Matched CASE-INSENSITIVELY, which is not fussiness. Live golems arrive
     * here as a Java class name ("StrawGolem"); sleepers are rebuilt from the
     * entity id instead, and since the registry name has no underscore that
     * produces "Strawgolem" with a small g. The old exact-match let that slip
     * through unconverted, so the same golem read "Harvester" while awake and
     * "Strawgolem" the moment it went to bed - and the summary line counted them
     * as two different trades.
     */
    private static String tradeFromClass(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()
                || simpleName.equalsIgnoreCase("StrawGolem")) {
            return "Harvester";
        }
        // Same reason: strip the suffix however it happens to be capitalised.
        return simpleName.length() > 5
                && simpleName.regionMatches(true, simpleName.length() - 5, "Golem", 0, 5)
                ? simpleName.substring(0, simpleName.length() - 5)
                : simpleName;
    }

    /** Distance if it's in our dimension, otherwise which dimension it's in. */
    private String describeRemote(org.hero.strawgolem.network.RosterEntry e) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return "";
        }
        if (!minecraft.level.dimension().location().toString().equals(e.dimension())) {
            String dim = e.dimension();
            return dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        }
        double d = Math.sqrt(e.pos().distToCenterSqr(
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()));
        return Math.round(d) + "m";
    }

    private static String tradeOf(StrawGolem golem) {
        String name = golem.getType().getDescription().getString();
        if (name.endsWith(" Golem")) {
            name = name.substring(0, name.length() - 6);
        }
        return name.equals("Straw") ? "Harvester" : name;
    }

    private static String rankName(int rank) {
        return Component.translatable("strawgolem.rank." + rank).getString();
    }

    private static int rankColor(int rank) {
        return switch (rank) {
            case 2 -> 0xFFD54F;
            case 1 -> 0xB0C4DE;
            default -> 0x7A7A7A;
        };
    }

    /** Lamp colour, short verdict, and the one line telling you what to do. */
    private record Health(int colour, String label, String detail) {}

    /**
     * Rows the server would also be able to count: within the locator's radius,
     * and actually in the world rather than tucked in a bindle.
     *
     * <p>{@code where} is a display string like "132m", so pull the leading
     * digits back out rather than widen the record for one comparison.
     */
    private int countInRange() {
        int limit = (int) org.hero.strawgolem.item.GolemDirectoryItem.LOCATE_RANGE;
        int n = 0;
        for (Row r : rows) {
            if (r.remote()) {
                continue;
            }
            int v = 0;
            boolean any = false;
            for (int i = 0; i < r.where().length(); i++) {
                char c = r.where().charAt(i);
                if (c >= '0' && c <= '9') {
                    v = v * 10 + (c - '0');
                    any = true;
                } else if (any) {
                    break;
                }
            }
            if (any && v <= limit) {
                n++;
            }
        }
        return n;
    }

    private static final int GREEN = 0xFF4CAF50;
    private static final int AMBER = 0xFFFFC107;
    private static final int RED = 0xFFE53935;

    /**
     * Triage the crew into one of three states, worst first.
     *
     * <p>Ordering matters: a desync means the numbers underneath cannot be
     * trusted, so it outranks anything the rows claim. Starvation comes next
     * because it is self-reinforcing - hunger throttles movement, so a hungry
     * golem gets slower at reaching the food that would fix it, and the whole
     * crew stalls out. Everything below that is worth a glance, not a panic.
     */
    private Health assess(int serverCount) {
        int n = rows.size();
        // Compare like for like. The server counts golems within LOCATE_RANGE;
        // this list covers everything the client has rendered, which reaches much
        // further. Comparing the two raw numbers cried DESYNC simply for standing
        // 130 blocks from the crew - so measure the client's in-range rows only.
        int nearby = countInRange();
        if (serverCount >= 0 && serverCount != nearby) {
            return new Health(RED, "ROSTER DESYNC",
                    "server sees " + serverCount + " within "
                            + (int) org.hero.strawgolem.item.GolemDirectoryItem.LOCATE_RANGE
                            + "m, this list has " + nearby
                            + (serverCount == 0 ? " - rows may be stale, try a relog"
                                                : " - client out of sync"));
        }
        if (n > 0 && hungryCount * 2 >= n) {
            return new Health(RED, "WORKFORCE STRIKE",
                    hungryCount + " starving - restock the Lunch Cart");
        }
        if (hungryCount > 0) {
            return new Health(AMBER, "PECKISH", hungryCount + " getting hungry");
        }
        if (n == 0) {
            return new Health(AMBER, "NOBODY HOME", "no crew in range");
        }
        return new Health(GREEN, "ALL CLEAR", "");
    }

    /**
     * The headcount the SERVER last measured, or -1 if unknown.
     *
     * <p>Written onto the clipboard as a component when you right-click it, so
     * it rides along with the item and needs no packet. Read fresh every frame,
     * which means it appears a tick after the screen opens rather than being
     * captured stale at open time.
     */
    private static int readServerCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return -1;
        }
        for (net.minecraft.world.item.ItemStack held : new net.minecraft.world.item.ItemStack[]{
                mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
            net.minecraft.world.item.component.CustomData data =
                    held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data == null) {
                continue;
            }
            String raw = data.copyTag().getString(
                    org.hero.strawgolem.item.GolemDirectoryItem.COUNT_KEY);
            int sep = raw.indexOf(':');
            if (sep <= 0) {
                continue;
            }
            try {
                return Integer.parseInt(raw.substring(0, sep));
            } catch (NumberFormatException ignored) {
                // stamp malformed - treat as unknown rather than guess
            }
        }
        return -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------- detail view

    private Row rowFor(java.util.UUID id) {
        for (Row r : rows) {
            if (id.equals(r.id())) {
                return r;
            }
        }
        return null;
    }

    private static final String[] RANK_NAME = {"Apprentice", "Journeyman", "Master"};
    private static final int[] RANK_AT = {0, 50, 200};

    /** One "Label   value" line. Returns the y for the next one. */
    private int stat(GuiGraphics g, int x, int y, String label, String value, int colour) {
        g.drawString(font, label, x, y, 0x9A8A78, false);
        g.drawString(font, value, x + 92, y, colour, false);
        return y + 12;
    }

    private void drawBack(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        String back = "< Back";
        backW = font.width(back) + 10;
        backH = 14;
        backX = left + PANEL_W - backW - 8;
        backY = top + 6;
        boolean over = mouseX >= backX && mouseX <= backX + backW
                && mouseY >= backY && mouseY <= backY + backH;
        g.fill(backX, backY, backX + backW, backY + backH, over ? 0x40FFFFFF : 0x20FFFFFF);
        g.drawString(font, back, backX + 5, backY + 3, over ? 0xFFFFFF : 0xC9B99F, false);
    }

    /**
     * Everything known about one golem.
     *
     * <p>The roster line ran out of room long before it ran out of things worth
     * showing: rank progress, pack contents, bound chest and home bunkhouse were
     * all either invisible or crushed into a column that clipped them.
     */
    private void renderDetail(GuiGraphics g, int left, int top, Row r, int mouseX, int mouseY) {
        g.drawString(font, Component.literal(r.name()).withStyle(ChatFormatting.GOLD),
                left + 8, top + 7, 0xFFFFFF, false);
        String sub = r.trade();
        if (r.assigned() != null && !r.assigned().isEmpty()) {
            sub = sub + "  -  " + r.assigned();
        }
        g.drawString(font, sub, left + 8, top + 19, 0x8AB4D8, false);
        drawBack(g, left, top, mouseX, mouseY);

        int x = left + 14;
        int y = top + 40;

        g.drawString(font, "STANDING", x, y, 0x6F5F4B, false);
        y += 12;
        int rank = Math.max(0, Math.min(2, r.rank()));
        String rankLine = RANK_NAME[rank];
        if (r.jobs() >= 0) {
            rankLine = rankLine + "  (" + r.jobs() + " jobs";
            if (rank < 2) {
                rankLine = rankLine + ", " + Math.max(0, RANK_AT[rank + 1] - r.jobs()) + " to go";
            }
            rankLine = rankLine + ")";
        }
        y = stat(g, x, y, "Rank", rankLine, rank == 2 ? 0xFFD54F : 0xE0D5C4);
        String life = r.immortal() ? "Timeless"
                : r.lifePct() < 0 ? "-" : r.lifePct() + "% spent";
        y = stat(g, x, y, "Lifespan", life,
                r.immortal() ? 0xB39DDB : r.lifePct() > 75 ? 0xE53935 : 0xE0D5C4);
        y = stat(g, x, y, "Fed",
                Constants.Golem.hunger ? (100 - r.hungerPct()) + "%" : "n/a",
                r.hungerPct() >= 60 ? 0xE53935 : 0x4CAF50);

        y += 6;
        g.drawString(font, "WORK", x, y, 0x6F5F4B, false);
        y += 12;
        String st = r.status();
        if (!r.where().isEmpty()) {
            st = st + "  " + r.where();
        }
        y = stat(g, x, y, "Status", st, 0xE0D5C4);
        String bag = !r.backpack() ? "None fitted"
                : r.bagCap() < 0 ? "Fitted"
                : r.bagUsed() + " / " + r.bagCap() + " slots";
        y = stat(g, x, y, "Backpack", bag, r.backpack() ? 0x4CAF50 : 0x9A8A78);
        String chest = r.chest() == null ? "Nothing bound"
                : r.chest().getX() + ", " + r.chest().getY() + ", " + r.chest().getZ();
        y = stat(g, x, y, "Deposits to", chest, r.chest() == null ? 0xFFC107 : 0xE0D5C4);
        String home = r.home() == null ? "No bunkhouse"
                : r.home().getX() + ", " + r.home().getY() + ", " + r.home().getZ();
        y = stat(g, x, y, "Sleeps at", home, r.home() == null ? 0xFFC107 : 0xE0D5C4);

        if (r.remote()) {
            g.drawString(font, "Second-hand entry - some details need the golem loaded.",
                    x, top + PANEL_H - 16, 0x6F5F4B, false);
        }
    }

    // ------------------------------------------------------------- legend view

    private void renderLegend(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        g.drawString(font, Component.literal("What this all means")
                .withStyle(ChatFormatting.GOLD), left + 8, top + 7, 0xFFFFFF, false);
        drawBack(g, left, top, mouseX, mouseY);

        int x = left + 14;
        int y = top + 32;
        String[][] items = {
            {"Fed bar", "How full they are. Green is fine, amber is peckish,"},
            {"", "red means they are slowing and heading for a Lunch Cart."},
            {"Timeless", "Given an Immortal Soul - stops ageing, never retires."},
            {"Rank", "Apprentice, Journeyman at 50 jobs, Master at 200."},
            {"", "Each rank adds 10% speed; Master also gets slower hunger."},
            {"Hauling", "Carrying something to a chest right now."},
            {"Asleep", "In a bunkhouse. Frozen: no ageing, no hunger, no work."},
            {"In bindle", "Packed in a Golem Bindle in somebody inventory."},
            {"Away", "The server knows it, but it is not loaded near you."},
        };
        for (String[] it : items) {
            if (!it[0].isEmpty()) {
                g.drawString(font, it[0], x, y, 0xFFD54F, false);
            }
            g.drawString(font, it[1], x + 66, y, 0xC9B99F, false);
            y += 12;
        }
        g.drawString(font, "Click any name on the roster for that golem full record.",
                x, top + PANEL_H - 16, 0x6F5F4B, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if ((selected != null || legend)
                    && mouseX >= backX && mouseX <= backX + backW
                    && mouseY >= backY && mouseY <= backY + backH) {
                selected = null;
                legend = false;
                return true;
            }
            if (selected == null && !legend) {
                if (mouseX >= legendX && mouseX <= legendX + legendW
                        && mouseY >= legendY && mouseY <= legendY + legendH) {
                    legend = true;
                    return true;
                }
                java.util.UUID hit = rowAt(mouseX, mouseY);
                if (hit != null) {
                    selected = hit;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Which roster row is under the cursor, if any. */
    private java.util.UUID rowAt(double mouseX, double mouseY) {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        if (mouseX < left || mouseX > left + PANEL_W) {
            return null;
        }
        int y = top + HEADER_H;
        int shown = visibleRows();
        for (int i = 0; i < shown && i + scroll < rows.size(); i++) {
            int ry = y + 2 + i * ROW_H;
            if (mouseY >= ry - 1 && mouseY < ry + ROW_H - 2) {
                return rows.get(i + scroll).id();
            }
        }
        return null;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Escape backs out one level rather than closing the whole tablet.
        if (key == 256 && (selected != null || legend)) {
            selected = null;
            legend = false;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

}
