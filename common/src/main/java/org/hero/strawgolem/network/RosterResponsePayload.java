package org.hero.strawgolem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.hero.strawgolem.Constants;

import java.util.List;

/**
 * Server -> client: the asking player's full roster, including golems that are
 * unloaded, asleep in a bunkhouse, or packed in a bindle - none of which the
 * client can see for itself.
 */
public record RosterResponsePayload(List<RosterEntry> entries) implements CustomPacketPayload {
    public static final Type<RosterResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MODID, "roster_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RosterResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    RosterEntry.LIST_CODEC, RosterResponsePayload::entries,
                    RosterResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
