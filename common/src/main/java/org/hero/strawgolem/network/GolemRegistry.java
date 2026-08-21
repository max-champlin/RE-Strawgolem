package org.hero.strawgolem.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A persistent record of every golem, who owns it, and where it was last seen.
 *
 * <p>Needed because a golem in an unloaded chunk is <b>not an entity</b> - not
 * on the client, and not on the server either. Scanning live entities can only
 * ever answer "which golems are near someone right now", which is precisely the
 * wrong question when one has gone missing. Golems checked into a bunkhouse are
 * worse still: they are stored as NBT and have no entity at all, even standing
 * next to the building.
 *
 * <p>So entries are written as golems tick (throttled) and at the moments they
 * leave the world in a known way, and they survive restarts. The registry is
 * kept on the OVERWORLD's storage and covers every dimension, so there is one
 * canonical list rather than one per level.
 */
public class GolemRegistry extends SavedData {
    private static final String NAME = "strawgolem_roster";
    /** How often a working golem refreshes its entry (~30s). Cheap, and stale-by-30s is fine. */
    public static final int UPDATE_INTERVAL = 600;

    private final Map<UUID, RosterEntry> entries = new HashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>(); // golem -> owner

    public static GolemRegistry get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(GolemRegistry::new, GolemRegistry::load, null), NAME);
    }

    /** Records or refreshes a golem's line. Owner may be null for unclaimed golems. */
    public void put(UUID golemId, UUID owner, RosterEntry entry) {
        entries.put(golemId, entry);
        if (owner != null) {
            owners.put(golemId, owner);
        }
        setDirty();
    }

    /** Drops a golem entirely - it died, or was removed for good. */
    public void remove(UUID golemId) {
        if (entries.remove(golemId) != null) {
            owners.remove(golemId);
            setDirty();
        }
    }

    /** Re-stamps just the state (asleep / stowed / working) without a full update. */
    public void setState(UUID golemId, String state) {
        RosterEntry e = entries.get(golemId);
        if (e != null) {
            entries.put(golemId, new RosterEntry(e.golemId(), e.name(), e.trade(), e.rank(),
                    e.immortal(), e.hunger(), state, e.dimension(), e.pos(), e.lastSeen()));
            setDirty();
        }
    }

    /**
     * Every golem this player owns, across all dimensions, loaded or not -
     * plus every UNCLAIMED golem.
     *
     * <p>Unowned golems must be included or they go invisible. Ownership is
     * only ever stamped on certain interactions, so most of a long-running
     * world's crew has no owner at all; those golems then showed up ONLY while
     * stood in a loaded chunk, and silently dropped off the directory the
     * moment you walked away from them. A golem that is alive, working and
     * eight blocks from its bunkhouse reading as missing is worse than a
     * stranger's golem appearing on a shared world - and an unowned golem is
     * nobody's by definition, so there is no one it could be poached from.
     */
    /**
     * How many golems this world knows about, in total.
     *
     * <p>Counts from the persistent record rather than from loaded entities, so
     * it includes golems asleep in a bunkhouse (stored as NBT in the block, not
     * present as entities), stowed in a bindle, or simply in an unloaded chunk.
     * A world-load headcount taken from entities alone reports 0 for a perfectly
     * healthy crew that happens to be in bed - which is exactly what it did.
     */
    public int size() {
        return entries.size();
    }

    public List<RosterEntry> forOwner(UUID owner) {
        List<RosterEntry> out = new ArrayList<>();
        for (Map.Entry<UUID, RosterEntry> e : entries.entrySet()) {
            UUID holder = owners.get(e.getKey());
            if (holder == null || holder.equals(owner)) {
                out.add(e.getValue());
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, RosterEntry> e : entries.entrySet()) {
            RosterEntry r = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("golem", r.golemId());
            UUID owner = owners.get(e.getKey());
            if (owner != null) {
                t.putUUID("owner", owner);
            }
            t.putString("name", r.name());
            t.putString("trade", r.trade());
            t.putInt("rank", r.rank());
            t.putBoolean("immortal", r.immortal());
            t.putInt("hunger", r.hunger());
            t.putString("state", r.state());
            t.putString("dim", r.dimension());
            t.putLong("pos", r.pos().asLong());
            t.putLong("seen", r.lastSeen());
            list.add(t);
        }
        tag.put("golems", list);
        return tag;
    }

    public static GolemRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        GolemRegistry reg = new GolemRegistry();
        ListTag list = tag.getList("golems", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("golem");
            reg.entries.put(id, new RosterEntry(
                    id,
                    t.getString("name"),
                    t.getString("trade"),
                    t.getInt("rank"),
                    t.getBoolean("immortal"),
                    t.getInt("hunger"),
                    t.getString("state"),
                    t.getString("dim"),
                    BlockPos.of(t.getLong("pos")),
                    t.getLong("seen")));
            if (t.hasUUID("owner")) {
                reg.owners.put(id, t.getUUID("owner"));
            }
        }
        return reg;
    }
}
