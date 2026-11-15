package com.talviom.bodycamhorror.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talviom.bodycamhorror.loot.LootSpawner;
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

        root.then(Commands.literal("start").executes(c -> start(c, false))
                .then(Commands.literal("test").executes(c -> start(c, true))));
        root.then(Commands.literal("skip").executes(c -> {
            c.getSource().sendSuccess(() -> Component.literal(MatchManager.INSTANCE.skip(c.getSource().getServer())), false);
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
        String where = t == null ? "removed from teams" : "now in " + t.displayName;
        c.getSource().sendSuccess(() -> Component.literal(players.size() + " player(s) " + where), true);
        return players.size();
    }

    private static int addSpawn(CommandContext<CommandSourceStack> c, BhTeam t) {
        ServerLevel level = c.getSource().getServer().overworld();
        MapData map = MapData.get(level);
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        map.spawns(t).add(pos);
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.literal(t.displayName + " spawn #" + map.spawns(t).size() + " at " + pos.toShortString()), true);
        return 1;
    }

    private static int clearSpawns(CommandContext<CommandSourceStack> c, BhTeam t) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        map.spawns(t).clear();
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.literal(t.displayName + " spawns cleared"), true);
        return 1;
    }

    private static int addLoot(CommandContext<CommandSourceStack> c) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        map.lootPoints().add(pos);
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Loot point #" + map.lootPoints().size() + " at " + pos.toShortString()), true);
        return 1;
    }

    private static int clearLoot(CommandContext<CommandSourceStack> c) {
        MapData map = MapData.get(c.getSource().getServer().overworld());
        map.lootPoints().clear();
        map.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Loot points cleared"), true);
        return 1;
    }

    private static int testLoot(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getServer().overworld();
        LootSpawner.clearGroundItems(level);
        int n = LootSpawner.spawn(level, MapData.get(level).lootPoints());
        c.getSource().sendSuccess(() -> Component.literal("Items spawned: " + n), false);
        return n;
    }

    private static int start(CommandContext<CommandSourceStack> c, boolean test) {
        MatchManager m = MatchManager.INSTANCE;
        ServerPlayer self = c.getSource().getPlayer();
        // В тестовом режиме тот, кто запускает и ещё без команды, сам встаёт в «Альфу».
        if (test && self != null && m.teamOf(self) == null) m.setTeam(c.getSource().getServer(), self, BhTeam.ALPHA);
        String err = m.start(c.getSource().getServer(), test);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(test
                ? "Test match started. /bh skip skips a phase, /bh stop stops, /bh horror fear 80 sets your fear"
                : "Match started"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        MatchManager m = MatchManager.INSTANCE;
        MapData map = MapData.get(c.getSource().getServer().overworld());
        c.getSource().sendSuccess(() -> Component.literal("State: " + m.state() + ", round " + m.round()
                + ", " + m.secondsLeft() + " s left. Spawns: alpha " + map.spawns(BhTeam.ALPHA).size()
                + ", bravo " + map.spawns(BhTeam.BRAVO).size() + ". Loot points: " + map.lootPoints().size()), false);
        c.getSource().sendSuccess(m::scoreLine, false);
        return 1;
    }
}
