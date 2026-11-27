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

    /** Лобби 1.0: выдача учебного набора и пульт «Запись» (их места в MapData, ставит LobbyBuilder). */
    @SubscribeEvent
    public void onLobbyUseBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (MatchManager.INSTANCE.running()) return;
        com.talviom.bodycamhorror.match.MapData map = com.talviom.bodycamhorror.match.MapData.get(p.server.overworld());
        if (map.lobby() == null || p.level() != p.server.overworld()) return;
        net.minecraft.core.BlockPos pos = e.getPos();
        if (pos.equals(map.lobbyPoint("kit")) || pos.equals(map.lobbyPoint("kit2"))) {
            LobbyTraining.giveKit(p);
        } else if (pos.equals(map.lobbyPoint("console"))) {
            LobbyTraining.pressRecord(p);
        } else {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /** Папку инструктажа не выбросить: она просто исчезает и через пару секунд снова в руках (LobbyTraining). */
    @SubscribeEvent
    public void onLobbyTossFolder(net.minecraftforge.event.entity.item.ItemTossEvent e) {
        if (e.getEntity().getItem().is(com.talviom.bodycamhorror.gear.ModGear.BRIEFING_FOLDER.get())) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onLobbyAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !MatchManager.INSTANCE.running() && LobbyManager.inLobby(p)) {
            e.setCanceled(true);
        }
    }
}
