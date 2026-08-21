package org.hero.strawgolem.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import org.hero.strawgolem.Constants;

import java.util.List;

/**
 * The mod's first and only networking: the Employee Directory asking the server
 * for the player's full roster, and the answer coming back.
 *
 * <p>Everything else in this mod deliberately avoids packets by riding on
 * SynchedEntityData, which is why there was no network layer until now. That
 * trick cannot work here - the whole point is reporting golems the client has
 * no entity for.
 */
public final class StrawNetwork {
    private StrawNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(StrawNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                RosterRequestPayload.TYPE,
                RosterRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    GolemRegistry registry = GolemRegistry.get(player.getServer());
                    if (registry == null) {
                        return;
                    }
                    List<RosterEntry> mine = registry.forOwner(player.getUUID());
                    PacketDistributor.sendToPlayer(player, new RosterResponsePayload(mine));
                }));

        registrar.playToClient(
                RosterResponsePayload.TYPE,
                RosterResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> RosterCache.accept(payload.entries())));

        Constants.LOG.info("Straw Golem roster networking registered.");
    }

    /** Client-side: ask the server for our roster. */
    public static void requestRoster() {
        RosterCache.markRequested();
        PacketDistributor.sendToServer(new RosterRequestPayload());
    }
}
