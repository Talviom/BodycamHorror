package com.talviom.bodycamhorror.tenant;

import com.talviom.bodycamhorror.BodycamHorror;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Существа мода: Жилец (THE TENANT). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BodycamHorror.MOD_ID);

    public static final RegistryObject<EntityType<TenantEntity>> TENANT = ENTITIES.register("tenant",
            () -> EntityType.Builder.of(TenantEntity::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.6f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("tenant"));

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(TENANT.get(), TenantEntity.createAttributes().build());
    }
}
