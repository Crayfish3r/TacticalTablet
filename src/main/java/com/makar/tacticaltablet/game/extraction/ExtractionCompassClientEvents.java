package com.makar.tacticaltablet.game.extraction;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExtractionCompassClientEvents {
    private static final String TAG_EVENT_ITEM = "tactical_event_item";
    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_TARGET_X = "extraction_target_x";
    private static final String TAG_TARGET_Z = "extraction_target_z";
    private static final String TAG_TARGET_DIMENSION = "extraction_target_dimension";
    private static final String EVENT_TYPE = "extraction_point";

    private ExtractionCompassClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                Items.RECOVERY_COMPASS,
                new ResourceLocation(TacticalTabletMod.MODID, "extraction_angle"),
                ExtractionCompassClientEvents::angle
        ));
    }

    private static float angle(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        if (!isExtractionCompass(stack)) return -1.0F;
        if (level == null || entity == null) return fallbackSpin(level);

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_TARGET_X) || !tag.contains(TAG_TARGET_Z)) {
            return fallbackSpin(level);
        }

        String targetDimension = tag.getString(TAG_TARGET_DIMENSION);
        if (targetDimension.isBlank() || !targetDimension.equals(level.dimension().location().toString())) {
            return fallbackSpin(level);
        }

        double targetX = tag.getInt(TAG_TARGET_X) + 0.5D;
        double targetZ = tag.getInt(TAG_TARGET_Z) + 0.5D;
        double dx = targetX - entity.getX();
        double dz = targetZ - entity.getZ();
        if (dx * dx + dz * dz < 0.0001D) {
            return 0.0F;
        }

        double targetAngle = Math.atan2(dz, dx) / (Math.PI * 2.0D);
        double entityYaw = Mth.positiveModulo(entity.getYRot() / 360.0D, 1.0D);
        return (float) Mth.positiveModulo(0.5D - (entityYaw - 0.25D - targetAngle), 1.0D);
    }

    private static boolean isExtractionCompass(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.RECOVERY_COMPASS)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.getBoolean(TAG_EVENT_ITEM)
                && EVENT_TYPE.equals(tag.getString(TAG_EVENT_TYPE));
    }

    private static float fallbackSpin(ClientLevel level) {
        if (level == null) return 0.0F;
        return (level.getGameTime() % 32L) / 32.0F;
    }
}
