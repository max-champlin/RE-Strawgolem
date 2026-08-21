package org.hero.strawgolem.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.hero.strawgolem.golem.StrawGolem;
import org.hero.strawgolem.registry.BlockRegistry;
import org.hero.strawgolem.registry.ItemRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The bunkhouse works like a beehive for golems: at night or in rain a golem
 * checks in (the entity is stored as NBT - safe from mobs, weather, and even
 * aging), and everyone is released at dawn once the rain has stopped.
 *
 * Base capacity is 4 bunks; each hay bale touching the bunkhouse adds two
 * more, up to 12. Expanding worker housing literally means piling on hay.
 */
public class BunkhouseBlockEntity extends BlockEntity {
    private static final int BASE_CAPACITY = 4;
    private static final int BUNKS_PER_HAY = 2;
    private static final int MAX_CAPACITY = 12;

    private final List<CompoundTag> sleepers = new ArrayList<>();
    /** Immortal Souls stocked here, spent to auto-retire Master golems. */
    private int bankedSouls = 0;
    /** When on, a sleeping golem that has reached Master is immortalized. */
    private boolean autoRetire = false;

    public BunkhouseBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.BUNKHOUSE_BLOCK_ENTITY.get(), pos, state);
    }

    public int occupants() {
        return sleepers.size();
    }

    public int bankedSouls() {
        return bankedSouls;
    }

    public boolean isAutoRetire() {
        return autoRetire;
    }

    /** Adds one soul to the reserve (right-clicked in with an Immortal Soul). */
    public void bankSoul() {
        bankedSouls++;
        setChanged();
    }

    /** Flips the auto-retire setting; returns the new state. */
    public boolean toggleAutoRetire() {
        autoRetire = !autoRetire;
        setChanged();
        return autoRetire;
    }

    /**
     * Spends banked souls to immortalize any sleeping Master-rank golem, so
     * veterans don't quietly die of old age while you're away. Player-driven:
     * nothing happens unless auto-retire is on AND souls are stocked.
     */
    private void doAutoRetire() {
        if (!autoRetire || bankedSouls <= 0) {
            return;
        }
        boolean any = false;
        for (CompoundTag sleeper : sleepers) {
            if (bankedSouls <= 0) {
                break;
            }
            if (!sleeper.getBoolean("immortal") && sleeper.getInt("jobsDone") >= StrawGolem.JOBS_MASTER) {
                sleeper.putBoolean("immortal", true);
                bankedSouls--;
                any = true;
            }
        }
        if (any) {
            setChanged();
            level.playSound(null, worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.5F, 1.5F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5,
                        12, 0.3, 0.3, 0.3, 0.03);
            }
        }
    }

    /** Drops any banked souls as items (used when the lodge is broken). */
    public void dropBankedSouls() {
        if (level == null || bankedSouls <= 0) {
            return;
        }
        int remaining = bankedSouls;
        while (remaining > 0) {
            int n = Math.min(remaining, 64);
            Block.popResource(level, worldPosition, new ItemStack(ItemRegistry.IMMORTAL_SOUL.get(), n));
            remaining -= n;
        }
        bankedSouls = 0;
        setChanged();
    }

    public int capacity() {
        if (level == null) {
            return BASE_CAPACITY;
        }
        int hay = 0;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-1, -1, -1), worldPosition.offset(1, 1, 1))) {
            if (!p.equals(worldPosition) && level.getBlockState(p).is(Blocks.HAY_BLOCK)) {
                hay++;
            }
        }
        return Math.min(MAX_CAPACITY, BASE_CAPACITY + BUNKS_PER_HAY * hay);
    }

    public boolean hasRoom() {
        return occupants() < capacity();
    }

    /** Tucks the golem into a bunk if there is room. The entity is despawned. */
    public boolean tryCheckIn(StrawGolem golem) {
        if (level == null || level.isClientSide || golem.isRemoved() || sleepers.size() >= capacity()) {
            return false;
        }
        golem.setHomePos(worldPosition.immutable());
        CompoundTag tag = new CompoundTag();
        if (!golem.save(tag)) {
            return false;
        }
        tag.remove("Passengers");
        // Verify the golem can be reloaded from what we just saved BEFORE we
        // discard the real one - if the round-trip fails, we keep the golem in
        // the world rather than storing a corpse that can never wake up.
        if (EntityType.loadEntityRecursive(tag, level, e -> e) == null) {
            org.hero.strawgolem.Constants.LOG.error(
                    "Bunkhouse at {}: refusing check-in, golem NBT failed a reload test - leaving it awake.",
                    worldPosition);
            return false;
        }
        sleepers.add(tag);
        golem.updateRoster(org.hero.strawgolem.network.RosterEntry.STATE_ASLEEP);
        golem.markExpectedRemoval(); // bunkhouse check-in - deliberate, don't log it as a loss
        golem.discard();
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.2F);
        return true;
    }

    /** Wakes everyone: golems pop back out next to the bunkhouse. */
    public int releaseAll() {
        if (level == null || level.isClientSide || sleepers.isEmpty()) {
            return 0;
        }
        int released = 0;
        // Fail-SAFE: a sleeper that can't be respawned is KEPT, never discarded,
        // so a golem is never silently lost. Failures are logged loudly.
        List<CompoundTag> kept = new ArrayList<>();
        for (CompoundTag tag : sleepers) {
            Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
            if (entity == null) {
                org.hero.strawgolem.Constants.LOG.error(
                        "Bunkhouse at {}: a sleeping golem FAILED to load - keeping its data (tag id={})",
                        worldPosition, tag.getString("id"));
                kept.add(tag);
                continue;
            }
            BlockPos out = findExit();
            entity.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (level.addFreshEntity(entity)) {
                released++;
            } else {
                org.hero.strawgolem.Constants.LOG.error(
                        "Bunkhouse at {}: could not re-add a woken golem - keeping its data (tag id={})",
                        worldPosition, tag.getString("id"));
                kept.add(tag);
            }
        }
        sleepers.clear();
        sleepers.addAll(kept);
        setChanged();
        if (released > 0) {
            org.hero.strawgolem.Constants.LOG.info("Bunkhouse at {}: released {} golem(s), {} kept.",
                    worldPosition, released, kept.size());
            level.playSound(null, worldPosition, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return released;
    }

    private boolean passable(BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    private boolean standable(BlockPos p) {
        // Two blocks of air and solid ground: a spot a golem can walk away from.
        return passable(p) && passable(p.above())
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    private BlockPos findExit() {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = worldPosition.relative(dir);
            if (standable(p)) {
                return p;
            }
        }
        // House on a ledge: try one step down.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = worldPosition.relative(dir).below();
            if (standable(p)) {
                return p;
            }
        }
        // Merely-empty beats nothing; roof is the last resort.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = worldPosition.relative(dir);
            if (passable(p)) {
                return p;
            }
        }
        return worldPosition.above();
    }

    /** Morning bell: daybreak = everyone back to work. Retires first.
     * Release is NOT gated on rain - golems wake at dawn regardless of weather.
     * Keeping them asleep through a daytime shower just burns work hours (and
     * under a dome, rain never reaches them to age them anyway). They only head
     * home at night (GoHomeGoal), so they won't bounce straight back in. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BunkhouseBlockEntity house) {
        if (level.getGameTime() % 20 != 0 || house.sleepers.isEmpty()) {
            return;
        }
        // Keep the roster honest about golems that are ASLEEP. A checked-in
        // golem is not an entity - it is NBT in this block - so it never ticks
        // and never registers itself. Without this the Employee Directory
        // simply loses everyone overnight and reads "3 on the books" when
        // fifteen are tucked up in here.
        if (level.getGameTime() % 600 == 0) {
            house.registerSleepers(level, pos);
        }
        house.doAutoRetire();
        if (level.isDay()) {
            house.releaseAll();
        }
    }

    /**
     * Writes a roster line for every golem currently asleep here, straight from
     * the stored NBT. Slow-ticked (~30s) because it only has to be roughly
     * current - the point is that sleepers EXIST on the roster, not that their
     * hunger is to the tick.
     */
    private void registerSleepers(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return;
        }
        org.hero.strawgolem.network.GolemRegistry reg =
                org.hero.strawgolem.network.GolemRegistry.get(level.getServer());
        if (reg == null) {
            return;
        }
        String dim = level.dimension().location().toString();
        for (CompoundTag tag : sleepers) {
            if (!tag.hasUUID("UUID")) {
                continue;
            }
            String type = tag.getString("id");
            if (type.contains(":")) {
                type = type.substring(type.indexOf(':') + 1);
            }
            // "artisan_golem" -> "ArtisanGolem" so it matches the live entities'
            // class simple names, which is what the directory formats from.
            StringBuilder trade = new StringBuilder();
            for (String part : type.split("_")) {
                if (!part.isEmpty()) {
                    trade.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                }
            }
            // CustomName is stored as a serialized text component. Rather than
            // pull in the codec just for a roster label, unwrap the common
            // {"text":"Gerald"} shape and fall back to the raw string.
            String name = tag.getString("CustomName");
            int textAt = name.indexOf("\"text\":\"");
            if (textAt >= 0) {
                int from = textAt + 8;
                int to = name.indexOf('"', from);
                name = to > from ? name.substring(from, to) : "";
            } else if (name.startsWith("\"") && name.endsWith("\"") && name.length() > 1) {
                name = name.substring(1, name.length() - 1);
            }
            // Fall back to the birth name. Without this a golem you never put a
            // name tag on reads "(unnamed)" the moment it goes to bed, even
            // though it has a perfectly good name while it is awake.
            if (name.isEmpty()) {
                name = tag.getString("birthName");
            }
            int jobs = tag.getInt("jobsDone");
            int rank = jobs >= 200 ? 2 : jobs >= 50 ? 1 : 0;
            reg.put(tag.getUUID("UUID"),
                    tag.hasUUID("owner") ? tag.getUUID("owner") : null,
                    new org.hero.strawgolem.network.RosterEntry(
                            tag.getUUID("UUID"), name, trade.toString(), rank,
                            tag.getBoolean("immortal"), tag.getInt("hunger"),
                            org.hero.strawgolem.network.RosterEntry.STATE_ASLEEP,
                            dim, pos, level.getGameTime()));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        list.addAll(sleepers);
        tag.put("Sleepers", list);
        tag.putInt("BankedSouls", bankedSouls);
        tag.putBoolean("AutoRetire", autoRetire);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sleepers.clear();
        ListTag list = tag.getList("Sleepers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            sleepers.add(list.getCompound(i));
        }
        bankedSouls = tag.getInt("BankedSouls");
        autoRetire = tag.getBoolean("AutoRetire");
    }
}
