package com.talviom.bodycamhorror.inventory;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BodycamHorror.MOD_ID);

    public static final RegistryObject<MenuType<BhInventoryMenu>> INVENTORY = MENUS.register("inventory",
            () -> IForgeMenuType.create((id, inv, buf) -> new BhInventoryMenu(id, inv)));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
