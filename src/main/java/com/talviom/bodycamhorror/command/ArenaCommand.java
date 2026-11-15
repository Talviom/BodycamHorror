package com.talviom.bodycamhorror.command;

import com.mojang.brigadier.CommandDispatcher;
import com.talviom.bodycamhorror.arena.ArenaBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * /bh arena build — построить тестовую арену рядом с тем местом, где вы стоите.
 * Заменяет блоки в области 41x41x6 и перезаписывает спавны и точки лута. Добавляется к /bh.
 */
public final class ArenaCommand {
    private ArenaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bh").requires(s -> s.hasPermission(2))
                .then(Commands.literal("arena").then(Commands.literal("build").executes(c -> {
                    BlockPos base = BlockPos.containing(c.getSource().getPosition()).offset(2, 0, 2);
                    String result = ArenaBuilder.build(c.getSource().getServer().overworld(), base);
                    c.getSource().sendSuccess(() -> Component.literal(result), true);
                    return 1;
                }))));
    }
}
