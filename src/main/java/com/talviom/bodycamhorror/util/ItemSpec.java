package com.talviom.bodycamhorror.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Разбор строки конфига вида "item_id;count;nbt" (nbt необязателен) в стопку предметов. */
public final class ItemSpec {
    private ItemSpec() {}

    /** @return стопка предметов или пустая стопка, если строка неверная */
    public static ItemStack parse(String spec) {
        String[] parts = spec.split(";", 3);
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0].trim()));
            if (item == null || item == Items.AIR) {
                BodycamHorror.LOGGER.warn("Unknown item in config: {}", spec);
                return ItemStack.EMPTY;
            }
            int count = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1].trim())) : 1;
            ItemStack stack = new ItemStack(item, count);
            if (parts.length > 2 && !parts[2].isBlank()) stack.setTag(TagParser.parseTag(parts[2].trim()));
            return stack;
        } catch (RuntimeException | CommandSyntaxException ex) {
            BodycamHorror.LOGGER.warn("Bad item entry {}: {}", spec, ex.getMessage());
            return ItemStack.EMPTY;
        }
    }
}
