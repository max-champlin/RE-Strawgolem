package org.hero.strawgolem.golem;

import net.minecraft.core.BlockPos;
import org.hero.strawgolem.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A short grace period for a golem that has just died.
 *
 * <p>Twenty-two Masters aged out in the field on 2026-09-04 with their own cores
 * sitting unspent in the lodge they were homed to. That specific bug is fixed -
 * a Master now retires where it stands - but a workforce is still mortal, and
 * losing one to a creeper thirty seconds before you noticed is its own kind of
 * annoying.
 *
 * <p>Deliberately narrow, because the alternative is a save file full of ghosts:
 *
 * <ul>
 *   <li><b>Memory only.</b> Nothing is written to disk and nothing survives a
 *       restart. Close the game and the dead stay dead.</li>
 *   <li><b>A small window.</b> {@link #WINDOW_MS} minutes, then the record is
 *       dropped. This is a second chance, not an undo stack.</li>
 *   <li><b>A hard cap.</b> {@link #MAX} graves; the oldest is discarded first,
 *       so a bad night cannot grow this without bound.</li>
 *   <li><b>Quiet.</b> One line when a golem is actually brought back. Deaths are
 *       already logged elsewhere and do not need a second voice here.</li>
 * </ul>
 *
 * <p>The cost lives at the call site: the lodge charges souls. What comes back
 * is genuinely the same golem - same name, same UUID, same jobs done, same rank
 * - but its lifespan starts again and it returns hungry and mortal. Coming back
 * is a reprieve, not an upgrade.
 */
public final class Graveyard {

    private Graveyard() {
    }

    /** How long a golem can be brought back, in real milliseconds. */
    public static final long WINDOW_MS = 10 * 60 * 1000L;

    /** Most graves held at once. Oldest goes first. */
    public static final int MAX = 16;

    /** What it costs at the lodge. Retirement is one soul; this is dearer. */
    public static final int SOUL_COST = 3;

    public record Grave(CompoundTag nbt, String name, BlockPos diedAt,
                        BlockPos home, long deadAt) {
    }

    private static final LinkedList<Grave> GRAVES = new LinkedList<>();

    /** Wipe everything. Called as a world comes up, so nothing crosses sessions. */
    public static synchronized void clear() {
        GRAVES.clear();
    }

    /** Record a golem the moment it dies. Never called for a normal despawn. */
    public static synchronized void remember(StrawGolem golem) {
        if (golem.level().isClientSide) {
            return;
        }
        try {
            CompoundTag tag = new CompoundTag();
            golem.save(tag);
            GRAVES.addLast(new Grave(tag, golem.displayName(),
                    golem.blockPosition(), golem.getHomePos(),
                    System.currentTimeMillis()));
            while (GRAVES.size() > MAX) {
                GRAVES.removeFirst();
            }
        } catch (Exception e) {
            Constants.LOG.warn("could not record a grave for {}", golem.displayName(), e);
        }
    }

    private static void expire() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        Iterator<Grave> it = GRAVES.iterator();
        while (it.hasNext()) {
            if (it.next().deadAt() < cutoff) {
                it.remove();
            }
        }
    }

    /** Who could still be brought back at this lodge, newest first. */
    public static synchronized List<Grave> pending(BlockPos lodge) {
        expire();
        List<Grave> out = new ArrayList<>();
        for (Grave g : GRAVES) {
            if (g.home() != null && withinLodge(g.home(), lodge)) {
                out.add(0, g);
            }
        }
        return out;
    }

    /** A golem's home may name any storey of its building. */
    private static boolean withinLodge(BlockPos home, BlockPos lodge) {
        return home.getX() == lodge.getX() && home.getZ() == lodge.getZ()
                && Math.abs(home.getY() - lodge.getY()) <= 2;
    }

    /**
     * Bring back the most recent golem homed at this lodge.
     *
     * @return the name of whoever came back, or null if there was nobody
     */
    public static synchronized String raise(ServerLevel level, BlockPos lodge, BlockPos to) {
        expire();
        for (Iterator<Grave> it = GRAVES.descendingIterator(); it.hasNext(); ) {
            Grave g = it.next();
            if (g.home() == null || !withinLodge(g.home(), lodge)) {
                continue;
            }
            Entity entity = EntityType.loadEntityRecursive(g.nbt(), level, e -> e);
            if (!(entity instanceof StrawGolem golem)) {
                it.remove();
                continue;
            }
            // A reprieve, not an upgrade: the clock restarts, but it comes back
            // hungry and as mortal as it was before. Retire it properly if you
            // want it to stop dying.
            golem.setLifeSpan(0);
            golem.setHunger(Constants.Golem.maxHunger / 2);
            golem.setHealth(golem.getMaxHealth());
            golem.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (!level.addFreshEntity(golem)) {
                Constants.LOG.warn("could not re-add {} from the graveyard", g.name());
                return null;
            }
            it.remove();
            Constants.LOG.info("RAISED | '{}' brought back at {} by the lodge at {}",
                    g.name(), to, lodge);
            return g.name();
        }
        return null;
    }
}
