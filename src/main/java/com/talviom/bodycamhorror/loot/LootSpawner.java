package com.talviom.bodycamhorror.loot;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.talviom.bodycamhorror.BhConfig;
import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Раскладывает случайный лут по точкам в начале каждого раунда. */
public final class LootSpawner {
    private record Entry(int weight, Item item, int count, String nbt) {}

    private LootSpawner() {}

    /** Убирает все лежащие на земле предметы, чтобы раунд начинался с чистой карты. */
    public static void clearGroundItems(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ItemEntity) toRemove.add(e);
        }
        toRemove.forEach(Entity::discard);
    }

    /**
     * Зеркальный лут: точки идут парами (0-1, 2-3, ...), одна в половине Alpha, другая в половине Bravo.
     * Обе точки пары получают одно и то же, а каждое оружие выпадает не больше одного раза на сторону за раунд.
     */
    public static int spawnPaired(ServerLevel level, List<BlockPos> points) {
        List<Entry> table = parseTable();
        if (table.isEmpty()) return 0;
        RandomSource rnd = level.getRandom();
        double chance = BhConfig.LOOT_CHANCE.get();
        int spawned = 0;
        for (int i = 0; i + 1 < points.size(); i += 2) {
            if (table.isEmpty()) break;
            if (rnd.nextDouble() > chance) continue;
            int total = table.stream().mapToInt(Entry::weight).sum();
            Entry entry = pick(table, total, rnd);
            if (entry.item() == GUN) table.remove(entry);
            drop(level, points.get(i), entry);
            drop(level, points.get(i + 1), entry);
            spawned += 2;
        }
        return spawned;
    }

    private static final Item GUN = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));

    private static void drop(ServerLevel level, BlockPos p, Entry entry) {
        ItemStack stack = new ItemStack(entry.item(), entry.count());
        if (entry.nbt() != null) {
            try {
                stack.setTag(TagParser.parseTag(entry.nbt()));
            } catch (CommandSyntaxException ex) {
                BodycamHorror.LOGGER.warn("Bad loot nbt {}: {}", entry.nbt(), ex.getMessage());
            }
        }
        ItemEntity item = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, stack, 0, 0, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    public static int spawn(ServerLevel level, List<BlockPos> points) {
        List<Entry> table = parseTable();
        if (table.isEmpty()) return 0;
        int total = table.stream().mapToInt(Entry::weight).sum();
        RandomSource rnd = level.getRandom();
        double chance = BhConfig.LOOT_CHANCE.get();
        int spawned = 0;
        for (BlockPos p : points) {
            if (rnd.nextDouble() > chance) continue;
            Entry entry = pick(table, total, rnd);
            ItemStack stack = new ItemStack(entry.item(), entry.count());
            if (entry.nbt() != null) {
                try {
                    stack.setTag(TagParser.parseTag(entry.nbt()));
                } catch (CommandSyntaxException ex) {
                    BodycamHorror.LOGGER.warn("Bad loot nbt {}: {}", entry.nbt(), ex.getMessage());
                }
            }
            ItemEntity item = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, stack, 0, 0, 0);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
            spawned++;
        }
        return spawned;
    }

    private static Entry pick(List<Entry> table, int total, RandomSource rnd) {
        int r = rnd.nextInt(total);
        for (Entry e : table) {
            r -= e.weight();
            if (r < 0) return e;
        }
        return table.get(table.size() - 1);
    }

    private static List<Entry> parseTable() {
        List<Entry> out = new ArrayList<>();
        for (String line : BhConfig.LOOT_TABLE.get()) {
            String[] parts = line.split(";", 4);
            if (parts.length < 3) {
                BodycamHorror.LOGGER.warn("Bad loot entry (need weight;item;count): {}", line);
                continue;
            }
            try {
                int weight = Integer.parseInt(parts[0].trim());
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[1].trim()));
                int count = Integer.parseInt(parts[2].trim());
                if (item == null || item == Items.AIR || weight <= 0) {
                    BodycamHorror.LOGGER.warn("Unknown loot item or zero weight: {}", line);
                    continue;
                }
                out.add(new Entry(weight, item, Math.max(1, count), parts.length == 4 ? parts[3].trim() : null));
            } catch (RuntimeException ex) {
                BodycamHorror.LOGGER.warn("Bad loot entry {}: {}", line, ex.getMessage());
            }
        }
        return out;
    }
}
