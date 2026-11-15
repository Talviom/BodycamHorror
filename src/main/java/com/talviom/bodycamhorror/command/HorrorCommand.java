package com.talviom.bodycamhorror.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.talviom.bodycamhorror.horror.HorrorDirector;
import com.talviom.bodycamhorror.horror.HorrorEvent;
import com.talviom.bodycamhorror.sabotage.Sabotage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /bh horror test footsteps|whisper|knock|flicker|glitch — проверить событие на себе
 * /bh power reset — включить все щитки и генераторы
 * Добавляется к основной команде /bh.
 */
public final class HorrorCommand {
    private HorrorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("test");
        for (HorrorEvent ev : HorrorEvent.values()) {
            test.then(Commands.literal(ev.id).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                HorrorDirector.INSTANCE.fire(ev, p);
                c.getSource().sendSuccess(() -> Component.literal("Событие: " + ev.id), false);
                return 1;
            }));
        }

        d.register(Commands.literal("bh").requires(s -> s.hasPermission(2))
                .then(Commands.literal("horror").then(test))
                .then(Commands.literal("power").then(Commands.literal("reset").executes(c -> {
                    Sabotage.resetAll(c.getSource().getServer().overworld());
                    c.getSource().sendSuccess(() -> Component.literal("Питание восстановлено"), true);
                    return 1;
                }))));
    }
}
