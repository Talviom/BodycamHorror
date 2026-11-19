package com.talviom.bodycamhorror.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.talviom.bodycamhorror.bots.BotPlayers;
import com.talviom.bodycamhorror.flashlight.Flashlight;
import com.talviom.bodycamhorror.horror.FearSystem;
import com.talviom.bodycamhorror.match.BhTeam;
import com.talviom.bodycamhorror.match.MatchManager;
import com.talviom.bodycamhorror.radio.RadioState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * Проверка мультиплеера без второго игрока. Добавляется к /bh.
 *
 * /bh bot add alpha|bravo [имя] — поставить бота туда, где вы стоите, и записать в команду
 * /bh bot remove <имя>|all       — убрать бота (как выход игрока с сервера)
 * /bh bot light <имя>            — бот щёлкает фонариком
 * /bh bot sprint <имя> on|off    — бот бежит (флаг бега), чтобы проверить, что Жилец слышит бег
 * /bh debug                      — состояние каждого игрока: команда, жив ли, страх, фонарик, рация
 *
 * Остальное делается обычными командами: /tp, /kill, /damage,
 * /execute as <бот> run bh horror fear 80, /execute as <бот> run bh horror test radio.
 */
public final class BotCommand {
    private static final SuggestionProvider<CommandSourceStack> BOT_NAMES =
            (c, b) -> SharedSuggestionProvider.suggest(BotPlayers.names(), b);

    private BotCommand() {}

    private static int sprint(CommandContext<CommandSourceStack> c, boolean on) {
        String name = StringArgumentType.getString(c, "name");
        ServerPlayer bot = BotPlayers.get(name);
        if (bot == null) {
            c.getSource().sendFailure(Component.literal("No bot named " + name));
            return 0;
        }
        bot.setSprinting(on);
        c.getSource().sendSuccess(() -> Component.literal(name + " sprinting: " + (on ? "on" : "off")), false);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add");
        for (BhTeam t : BhTeam.values()) {
            add.then(Commands.literal(t.id).executes(c -> addBot(c, t, null))
                    .then(Commands.argument("name", StringArgumentType.word())
                            .executes(c -> addBot(c, t, StringArgumentType.getString(c, "name")))));
        }

        d.register(Commands.literal("bh").requires(s -> s.hasPermission(2))
                .then(Commands.literal("bot")
                        .then(add)
                        .then(Commands.literal("remove")
                                .then(Commands.literal("all").executes(c -> {
                                    int n = BotPlayers.removeAll(c.getSource().getServer());
                                    c.getSource().sendSuccess(() -> Component.literal("Bots removed: " + n), true);
                                    return n;
                                }))
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES).executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    if (!BotPlayers.remove(c.getSource().getServer(), name)) {
                                        c.getSource().sendFailure(Component.literal("No bot named " + name));
                                        return 0;
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal("Bot removed: " + name), true);
                                    return 1;
                                })))
                        .then(Commands.literal("light")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES).executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    ServerPlayer found = BotPlayers.get(name);
                                    // Годится и обычный игрок: так сценарий щёлкает фонариком за хозяина мира.
                                    if (found == null) found = c.getSource().getServer().getPlayerList().getPlayerByName(name);
                                    if (found == null) {
                                        c.getSource().sendFailure(Component.literal("No player named " + name));
                                        return 0;
                                    }
                                    ServerPlayer bot = found;
                                    Flashlight.toggle(bot);
                                    c.getSource().sendSuccess(() -> Component.literal(name + " flashlight: "
                                            + (Flashlight.isOn(bot) ? "on" : "off")), false);
                                    return 1;
                                })))
                        // Бег для проверки слуха Жильца: настоящий клиент из сценария не разогнать, а бот
                        // просто держит флаг бега (сервер слышит бег по isSprinting, без движения тоже).
                        .then(Commands.literal("sprint")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(BOT_NAMES)
                                        .then(Commands.literal("on").executes(c -> sprint(c, true)))
                                        .then(Commands.literal("off").executes(c -> sprint(c, false))))))
                .then(Commands.literal("debug").executes(BotCommand::debug)
                        .then(Commands.literal("forgetlights").executes(c -> {
                            Flashlight.debugForget();
                            c.getSource().sendSuccess(() -> Component.literal("Flashlights forgotten, light blocks left in the world"), false);
                            return 1;
                        }))));
    }

    private static int addBot(CommandContext<CommandSourceStack> c, BhTeam team, String name) {
        MinecraftServer server = c.getSource().getServer();
        if (name == null) {
            String prefix = team == BhTeam.ALPHA ? "BotA" : "BotB";
            int i = 1;
            while (server.getPlayerList().getPlayerByName(prefix + i) != null) i++;
            name = prefix + i;
        }
        if (name.length() > 16) {
            c.getSource().sendFailure(Component.literal("Name is longer than 16 characters"));
            return 0;
        }
        Entity self = c.getSource().getEntity();
        ServerPlayer bot = BotPlayers.spawn(server, name, c.getSource().getPosition(), self == null ? 0f : self.getYRot());
        if (bot == null) {
            c.getSource().sendFailure(Component.literal("A player named " + name + " is already online"));
            return 0;
        }
        MatchManager.INSTANCE.setTeam(server, bot, team);
        String n = name;
        c.getSource().sendSuccess(() -> Component.literal("Bot " + n + " joined " + team.displayName), true);
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        MatchManager m = MatchManager.INSTANCE;
        c.getSource().sendSuccess(() -> Component.literal("[bh] state=" + m.state() + " round=" + m.round()
                + " left=" + m.secondsLeft() + "s score=" + m.score(BhTeam.ALPHA) + ":" + m.score(BhTeam.BRAVO)
                + " radio=" + RadioState.alive().size() + " lights=" + Flashlight.debugLights()), false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BhTeam t = m.teamOf(p);
            Entity cam = p.getCamera();
            String line = String.format(Locale.ROOT,
                    "[bh] %s%s team=%s alive=%s mode=%s hp=%.0f fear=%.0f light=%s/%s bat=%.0f%% cam=%s radio=%s pos=%s",
                    p.getGameProfile().getName(), BotPlayers.isBot(p) ? "(bot)" : "",
                    t == null ? "-" : t.id, m.isAlive(p), p.gameMode.getGameModeForPlayer().getName(), p.getHealth(),
                    FearSystem.INSTANCE.fear(p), Flashlight.isOn(p) ? "on" : "off", Flashlight.isLit(p) ? "lit" : "dark",
                    Flashlight.charge(p) * 100f, cam == p ? "self" : cam.getName().getString(),
                    RadioState.isJammed(p.getUUID()) ? "jammed" : "ok",
                    p.blockPosition().toShortString());
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
