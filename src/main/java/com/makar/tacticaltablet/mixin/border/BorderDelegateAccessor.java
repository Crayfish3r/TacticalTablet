package com.makar.tacticaltablet.mixin.border;

import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BorderChangeListener.DelegateBorderChangeListener.class)
public interface BorderDelegateAccessor {
    @Accessor("worldBorder")
    WorldBorder tacticaltablet$getWorldBorder();
}
