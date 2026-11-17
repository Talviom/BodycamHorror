package com.talviom.bodycamhorror.lobby;

import com.talviom.bodycamhorror.match.MatchManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Лобби: таймер возврата, площадки команд и защита от урона, пока матч не идёт. */
public class LobbyEvents {
    @SubscribeEvent
    public void onLobbyServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) LobbyManager.INSTANCE.tick(server);
    }

    @SubscribeEvent
    public void onLobbyAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !MatchManager.INSTANCE.running() && LobbyManager.inLobby(p)) {
            e.setCanceled(true);
        }
    }
}
