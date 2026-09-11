package com.makar.tacticaltablet.camouflage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class CamouflagePiece {
    private final CamouflageTarget target;
    private final ResourceLocation itemId;
    private final CompoundTag itemTag;
    private final CamouflagePolicy policy;

    public CamouflagePiece(CamouflageTarget target, ResourceLocation itemId, CompoundTag itemTag,
                            CamouflagePolicy policy) {
        this.target = Objects.requireNonNull(target, "target");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.itemTag = Objects.requireNonNull(itemTag, "itemTag").copy();
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public CamouflageTarget target() { return target; }
    public ResourceLocation itemId() { return itemId; }
    public CompoundTag itemTag() { return itemTag.copy(); }
    public CamouflagePolicy policy() { return policy; }
}
