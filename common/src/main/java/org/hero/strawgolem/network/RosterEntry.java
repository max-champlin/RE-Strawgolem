package org.hero.strawgolem.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * One golem's line in the Employee Directory, as the SERVER knows it.
 *
 * <p>The directory originally read golem state straight off the client's own
 * entities, which needs no networking at all - but it can only ever show golems
 * the client has loaded. That is exactly the wrong set when the question is
 * "where did my golem go?", because the missing one is usually the one nobody
 * is standing near. These entries come from the persistent registry instead, so
 * a golem asleep in a bunkhouse three thousand blocks away still has a row.
 *
 * @param golemId   stable identity across bunkhouse check-ins, which recreate
 *                  the entity (and therefore change its entity id) every dawn
 * @param name      custom name, or empty for an unnamed golem
 * @param trade     class simple name, e.g. "ArtisanGolem"
 * @param state     see {@link #STATE_WORKING} and friends
 * @param dimension dimension id, e.g. "minecraft:overworld"
 * @param lastSeen  world game-time of the last update, for staleness
 */
public record RosterEntry(
        UUID golemId,
        String name,
        String trade,
        int rank,
        boolean immortal,
        int hunger,
        String state,
        String dimension,
        BlockPos pos,
        long lastSeen
) {
    /** Out in the world doing its job. */
    public static final String STATE_WORKING = "working";
    /** Checked into a bunkhouse - not an entity right now, so nothing can see it. */
    public static final String STATE_ASLEEP = "asleep";
    /** Packed in a Golem Bindle. */
    public static final String STATE_STOWED = "stowed";

    public static final StreamCodec<RegistryFriendlyByteBuf, RosterEntry> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RosterEntry decode(RegistryFriendlyByteBuf buf) {
                    return new RosterEntry(
                            buf.readUUID(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readBlockPos(),
                            buf.readVarLong());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RosterEntry e) {
                    buf.writeUUID(e.golemId());
                    buf.writeUtf(e.name());
                    buf.writeUtf(e.trade());
                    buf.writeVarInt(e.rank());
                    buf.writeBoolean(e.immortal());
                    buf.writeVarInt(e.hunger());
                    buf.writeUtf(e.state());
                    buf.writeUtf(e.dimension());
                    buf.writeBlockPos(e.pos());
                    buf.writeVarLong(e.lastSeen());
                }
            };

    public static final StreamCodec<RegistryFriendlyByteBuf, java.util.List<RosterEntry>> LIST_CODEC =
            ByteBufCodecs.collection(java.util.ArrayList::new, STREAM_CODEC);
}
