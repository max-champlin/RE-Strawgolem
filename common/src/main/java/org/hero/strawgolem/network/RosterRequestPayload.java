package org.hero.strawgolem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.hero.strawgolem.Constants;

/**
 * Client -> server: "send me every golem I own." Carries no data - the server
 * already knows who is asking - so the codec is a constant.
 */
public record RosterRequestPayload() implements CustomPacketPayload {
    public static final Type<RosterRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MODID, "roster_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RosterRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new RosterRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
