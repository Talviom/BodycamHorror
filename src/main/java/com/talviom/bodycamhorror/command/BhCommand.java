package com.talviom.bodycamhorror.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talviom.bodycamhorror.ModBlocks;
import com.talviom.bodycamhorror.arena.LobbyBuilder;
import com.talviom.bodycamhorror.lobby.LobbyManager;
import com.talviom.bodycamhorror.loot.LootSpawner;
import com.talviom.bodycamhorror.objective.TapeObjective;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MapData;
import com.talviom.bodycamhorror.match.MatchManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * /bh team alpha|bravo <игроки>   — распределить игроков по командам
 * /bh team leave <игроки>          — убрать из команд
 * /bh spawn add|clear alpha|bravo — точки спавна (add ставит точку там, где вы стоите)
 * /bh loot add|clear|test          — точки лута
 * /bh exit set alpha|bravo         — выход стороны карты (там, где вы стоите), туда выносят кассету
 * /bh exit clear alpha|bravo
 * /bh tape add|clear|test          — точки, где появляется кассета (test кладёт кассету в случайную точку)
 * /bh lobby build                  — построить лобби там, где вы стоите
 * /bh lobby set                    — точка лобби там, где вы стоите
 * /bh lobby pad alpha|bravo        — площадка выбора команды там, где вы стоите
 * /bh lobby return                 — отправить всех в лобби
 * /bh start | stop | status
 * /bh start test                   — тестовый матч, можно одному
 * /bh skip                         — перемотать текущую фазу (заморозка, раунд, пауза)
 */
public final class BhCommand {
    private BhCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bh").requires(s -> s.hasPermission(2));

        LiteralArgumentBuilder<CommandSourceStack> team = Commands.literal("team");
        for (BhTeam t : BhTeam.values()) {
            team.then(Commands.literal(t.id).then(Commands.argument("players", EntityArgument.players())
                    .executes(c -> setTeam(c, t))));
        }
        team.then(Commands.literal("leave").then(Commands.argument("players", EntityArgument.players())
                .executes(c -> setTeam(c, null))));
        root.then(team);

        LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
        LiteralArgumentBuilder<CommandSourceStack> spawnAdd = Commands.literal("add");
        LiteralArgumentBuilder<CommandSourceStack> spawnClear = Commands.literal("clear");
        for (BhTeam t : BhTeam.values()) {
            spawnAdd.then(Commands.literal(t.id).executes(c -> addSpawn(c, t)));
            spawnClear.then(Commands.literal(t.id).executes(c -> clearSpawns(c, t)));
        }
        root.then(spawn.then(spawnAdd).then(spawnClear));

        root.then(Commands.literal("loot")
                .then(Commands.literal("add").executes(BhCommand::addLoot))
                .then(Commands.literal("clear").executes(BhCommand::clearLoot))
                .then(Commands.literal("test").executes(BhCommand::testLoot)));

        LiteralArgumentBuilder<CommandSourceStack> exitSet = Commands.literal("set");
        LiteralArgumentBuilder<CommandSourceStack> exitClear = Commands.literal("clear");
        LiteralArgumentBuilder<CommandSourceStack> padSet = Commands.literal("pad");
        for (BhTeam t : BhTeam.values()) {
            exitSet.then(Commands.literal(t.id).executes(c -> setExit(c, t)));
            exitClear.then(Commands.literal(t.id).executes(c -> clearExit(c, t)));
            padSet.then(Commands.literal(t.id).executes(c -> setPad(c, t)));
        }
        root.then(Commands.literal("exit").then(exitSet).then(exitClear));

        root.then(Commands.literal("tape")
                .then(Commands.literal("add").executes(BhCommand::addTapePoint))
                .then(Commands.literal("clear").executes(BhCommand::clearTapePoints))
                .then(Commands.literal("test").executes(BhCommand::testTape)));

        root.then(Commands.literal("lobby")
                .then(Commands.literal("build").executes(BhCommand::buildLobby))
                .then(Commands.literal("set").executes(BhCommand::setLobby))
                .then(padSet)
                .then(Commands.literal("return").executes(c -> {
                    LobbyManager.INSTANCE.sendAllToLobby(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.lobby_return"), true);
                    return 1;
                })));

        root.then(Commands.literal("start").executes(c -> start(c, false))
                .then(Commands.literal("test").executes(c -> start(c, true))));
        root.then(Commands.literal("skip").executes(c -> {
            c.getSource().sendSuccess(() -> MatchManager.INSTANCE.skip(c.getSource().getServer()), false);
            return 1;
        }));
        root.then(Commands.literal("stop").executes(c -> {
            MatchManager.INSTANCE.stop(c.getSource().getServer());
            return 1;
        }));
        root.then(Commands.literal("status").executes(BhCommand::status));

        d.register(root);
    }

    private static int setTeam(CommandContext<CommandSourceStack> c, BhTeam t) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer p : players) {
            if (t == null) MatchManager.INSTANCE.removeFromTeam(c.getSource().getServer(), p);
            else MatchManager.INSTANCE.setTeam(c.getSource().getServer(), p, t);
        }
        Component msg = t == null ? Component.translatable("bh.cmd.team_removed", players.size())
                : Component.translatable("bh.cmd.team_set", players.size(), t.title());
        c.getSource().sendSuccess(() -> msg, true);
        return players.size();
    }

    private static int addSpawn(CommandContext<CommandSourceStack> c, BhTeam t) {
        ServerLevel level = c.getSource().getServer().overworld();
        MapData map = MapData.get(level);
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        map.spawns(t).add(pos);
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.spawn_added", t.title(), map.spawns(t).size(), pos.toShortString()), true);
        return 1;
    }

    private static int clearSpawns(CommandContext<CommandSourceStack> c, BhTeam t) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        map.spawns(t).clear();
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.spawns_cleared", t.title()), true);
        return 1;
    }

    private static int addLoot(CommandContext<CommandSourceStack> c) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        map.lootPoints().add(pos);
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.loot_added", map.lootPoints().size(), pos.toShortString()), true);
        return 1;
    }

    private static int clearLoot(CommandContext<CommandSourceStack> c) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        map.lootPoints().clear();
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.loot_cleared"), true);
        return 1;
    }

    private static int testLoot(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        LootSpawner.clearGroundItems(level);
        int n = LootSpawner.spawn(level, MapData.get(level).lootPoints());
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.loot_spawned", n), false);
        return n;
    }

    private static int setExit(CommandContext<CommandSourceStack> c, BhTeam side) {
        ServerLevel level = c.getSource().getServer().overworld();
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        MapData.get(level).setExit(side, pos);
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, ModBlocks.EXIT_MARKER.get().defaultBlockState(), 3);
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.exit_set", side.title(), pos.toShortString()), true);
        return 1;
    }

    private static int clearExit(CommandContext<CommandSourceStack> c, BhTeam side) {
        MapData.get(c.getSource().getServer().overworld()).setExit(side, null);
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.exit_cleared", side.title()), true);
        return 1;
    }

    private static int addTapePoint(CommandContext<CommandSourceStack> c) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        map.tapePoints().add(pos);
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.tape_added", map.tapePoints().size(), pos.toShortString()), true);
        return 1;
    }

    private static int clearTapePoints(CommandContext<CommandSourceStack> c) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        map.tapePoints().clear();
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.tape_cleared"), true);
        return 1;
    }

    private static int testTape(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        MapData map = MapData.get(level);
        if (map.tapePoints().isEmpty()) {
            c.getSource().sendFailure(Component.translatable("bh.cmd.tape_none"));
            return 0;
        }
        BlockPos p = map.tapePoints().get(level.getRandom().nextInt(map.tapePoints().size()));
        TapeObjective.spawnTape(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(p));
        Component dropped = Component.translatable("bh.cmd.tape_dropped", p.toShortString());
        if (!TapeObjective.mapReady(level)) dropped = dropped.copy().append(Component.translatable("bh.cmd.tape_needs_exits"));
        Component msg = dropped;
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int buildLobby(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        Component msg = LobbyBuilder.build(level, BlockPos.containing(c.getSource().getPosition()));
        c.getSource().sendSuccess(() -> msg, true);
        return 1;
    }

    private static int setLobby(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        MapData.get(level).setLobby(pos);
        level.setDefaultSpawnPos(pos, 0f);
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.lobby_set", pos.toShortString()), true);
        return 1;
    }

    private static int setPad(CommandContext<CommandSourceStack> c, BhTeam t) {
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        MapData.get(c.getSource().getServer().overworld()).setLobbyPad(t, pos);
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.pad_set", t.title(), pos.toShortString()), true);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> c, boolean test) {
        MatchManager m = MatchManager.INSTANCE;
        ServerPlayer self = c.getSource().getPlayer();
        // В тестовом режиме тот, кто запускает и ещё без команды, сам встаёт в «Альфу».
        if (test && self != null && m.teamOf(self) == null) m.setTeam(c.getSource().getServer(), self, BhTeam.ALPHA);
        Component err = m.start(c.getSource().getServer(), test);
        if (err != null) {
            c.getSource().sendFailure(err);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(test ? "bh.cmd.test_started" : "bh.cmd.started"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        MatchManager m = MatchManager.INSTANCE;
        MapData map = MapData.get(c.getSource().getServer().overworld());
        c.getSource().sendSuccess(() -> Component.translatable("bh.cmd.status", m.state().label(), m.round(), m.secondsLeft(),
                map.spawns(BhTeam.ALPHA).size(), map.spawns(BhTeam.BRAVO).size(), map.lootPoints().size(),
                Component.translatable(map.exit(BhTeam.ALPHA) != null ? "bh.cmd.yes" : "bh.cmd.no"),
                Component.translatable(map.exit(BhTeam.BRAVO) != null ? "bh.cmd.yes" : "bh.cmd.no"),
                map.tapePoints().size(),
                map.lobby() != null ? Component.literal(map.lobby().toShortString()) : Component.translatable("bh.cmd.none")), false);
        c.getSource().sendSuccess(m::scoreLine, false);
        return 1;
    }
}
