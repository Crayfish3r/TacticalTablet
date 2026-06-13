package com.makar.tacticaltablet.corpse;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class CorpseEntity extends LivingEntity {

    public static final int CONTAINER_SIZE = 27;
    private static final int LIFETIME_TICKS = 20 * 60;

    private static final EntityDataAccessor<String> OWNER_ID = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_VALUE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_SIGNATURE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);

    private final SimpleContainer loot = new SimpleContainer(CONTAINER_SIZE);

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPose(Pose.SLEEPING);
        setHealth(1.0F);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public void initialize(UUID ownerId, String ownerName, String skinValue, String skinSignature, List<ItemStack> stacks) {
        entityData.set(OWNER_ID, ownerId == null ? "" : ownerId.toString());
        entityData.set(OWNER_NAME, ownerName == null ? "" : ownerName);
        entityData.set(SKIN_VALUE, skinValue == null ? "" : skinValue);
        entityData.set(SKIN_SIGNATURE, skinSignature == null ? "" : skinSignature);
        setCustomName(Component.literal(getOwnerName()));
        setCustomNameVisible(false);

        loot.clearContent();
        for (int i = 0; i < stacks.size() && i < loot.getContainerSize(); i++) {
            loot.setItem(i, stacks.get(i).copy());
        }
    }

    public GameProfile createGameProfile() {
        UUID uuid = getOwnerId();
        GameProfile profile = new GameProfile(uuid, getOwnerName());
        String skinValue = getSkinValue();
        if (!skinValue.isBlank()) {
            profile.getProperties().put("textures", new Property("textures", skinValue, getSkinSignature()));
        }
        return profile;
    }

    public String getOwnerName() {
        String ownerName = entityData.get(OWNER_NAME);
        return ownerName.isBlank() ? "Игрок" : ownerName;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        setPose(Pose.SLEEPING);

        if (!level().isClientSide && (tickCount >= LIFETIME_TICKS || isLootEmpty())) {
            removeWithLoot(Entity.RemovalReason.DISCARDED);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        UUID ownerId = getOwnerId();
        if (ownerId != null && ownerId.equals(serverPlayer.getUUID()) && !CorpseTestManager.canLootOwnCorpses()) {
            serverPlayer.displayClientMessage(Component.literal("Вы не можете лутать свой труп."), true);
            return InteractionResult.CONSUME;
        }

        if (isLootEmpty()) {
            removeWithLoot(Entity.RemovalReason.DISCARDED);
            return InteractionResult.CONSUME;
        }

        serverPlayer.openMenu(new SimpleMenuProvider(
                (windowId, inventory, ignored) -> ChestMenu.threeRows(windowId, inventory, loot),
                Component.literal("Труп: " + getOwnerName())
        ));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(1.9F, 0.5F);
    }

    @Override
    public Direction getBedOrientation() {
        return Direction.fromYRot(getYRot());
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER_ID, "");
        entityData.define(OWNER_NAME, "");
        entityData.define(SKIN_VALUE, "");
        entityData.define(SKIN_SIGNATURE, "");
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    private UUID getOwnerId() {
        String value = entityData.get(OWNER_ID);
        if (value.isBlank()) return null;

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String getSkinValue() {
        return entityData.get(SKIN_VALUE);
    }

    private String getSkinSignature() {
        return entityData.get(SKIN_SIGNATURE);
    }

    private boolean isLootEmpty() {
        for (int slot = 0; slot < loot.getContainerSize(); slot++) {
            if (!loot.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void removeWithLoot(Entity.RemovalReason reason) {
        loot.clearContent();
        remove(reason);
    }
}
