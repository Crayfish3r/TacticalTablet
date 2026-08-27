package com.makar.tacticaltablet.integration.moderndamage;

import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.moderndamage.control.config.EffectEntry;
import com.moderndamage.control.config.ModClothConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Direct API adapter for the single verified MDC version. Do not reference MDC outside this package. */
final class ModernDamageAdapterV1032 implements ModernDamageAdapter {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("moderndamage.json5");
    private static final Path BACKUP_PATH = FMLPaths.CONFIGDIR.get().resolve("moderndamage.json5.tacticaltablet.bak");
    private final AtomicFileStore files = new AtomicFileStore();

    @Override
    public ModernDamageBalanceSnapshot readBalance(long revision) {
        ModClothConfig config = ModClothConfig.get();
        double[] values = new double[ModernDamageBalanceSchema.fields().size()];
        for (ModernDamageBalanceSchema.Field field : ModernDamageBalanceSchema.fields()) {
            values[field.id()] = readField(config, field);
        }
        return new ModernDamageBalanceSnapshot(revision, values);
    }

    @Override
    public void applyBalance(double[] values) throws Exception {
        if (values == null || values.length != ModernDamageBalanceSchema.fields().size()) {
            throw new IllegalArgumentException("Incomplete MDC balance snapshot");
        }
        ModClothConfig config = ModClothConfig.get();
        double[] previous = readBalance(0L).values();
        byte[] previousFile = backupExistingConfig();
        try {
            writeFields(config, values);
            AutoConfig.getConfigHolder(ModClothConfig.class).save();
        } catch (Exception exception) {
            writeFields(config, previous);
            restoreConfigFile(previousFile, exception);
            throw exception;
        }
    }

    private byte[] backupExistingConfig() throws IOException {
        if (!Files.isRegularFile(CONFIG_PATH)) return null;
        byte[] bytes = Files.readAllBytes(CONFIG_PATH);
        FileSaveResult backup = files.writeBytes(BACKUP_PATH, bytes);
        if (backup.status() != FileSaveResult.Status.SUCCESS) {
            IOException failure = new IOException(backup.diagnostic());
            backup.exception().ifPresent(failure::addSuppressed);
            throw failure;
        }
        return bytes;
    }

    private void restoreConfigFile(byte[] previousFile, Exception original) {
        if (previousFile == null) return;
        FileSaveResult restored = files.writeBytes(CONFIG_PATH, previousFile);
        if (restored.status() != FileSaveResult.Status.SUCCESS) {
            IOException failure = new IOException(restored.diagnostic());
            restored.exception().ifPresent(failure::addSuppressed);
            original.addSuppressed(failure);
        }
    }

    private static void writeFields(ModClothConfig config, double[] values) {
        for (ModernDamageBalanceSchema.Field field : ModernDamageBalanceSchema.fields()) {
            writeField(config, field, values[field.id()]);
        }
    }

    private static double readField(ModClothConfig config, ModernDamageBalanceSchema.Field field) {
        if (field.metric() != ModernDamageBalanceSchema.Metric.VALUE) {
            EffectEntry entry = injuryEntry(config, field);
            return switch (field.metric()) {
                case THRESHOLD -> entry.threshold;
                case CHANCE -> entry.chance;
                case DURATION -> entry.duration;
                case VALUE -> throw new IllegalStateException("Unexpected scalar metric");
            };
        }
        return switch (field.key()) {
            case "minorBleedingIntervalTicks" -> config.minorBleedingIntervalTicks;
            case "minorBleedingDamagePerLevel" -> config.minorBleedingDamagePerLevel;
            case "majorBleedingIntervalTicks" -> config.majorBleedingIntervalTicks;
            case "majorBleedingDamagePerLevel" -> config.majorBleedingDamagePerLevel;
            case "meleeAttackCost" -> config.meleeAttackCost;
            case "bowDrawCostPerTick" -> config.bowDrawCostPerTick;
            case "adsCostPerTick" -> config.adsCostPerTick;
            case "miningCostPerBlock" -> config.miningCostPerBlock;
            case "staminaRegenDelayTicks" -> config.staminaRegenDelayTicks;
            case "legSprintingCostPerTick" -> config.legSprintingCostPerTick;
            case "legSwimmingCostPerTick" -> config.legSwimmingCostPerTick;
            case "legJumpCost" -> config.legJumpCost;
            case "legCrouchEnterCost" -> config.legCrouchEnterCost;
            case "legCrouchExitCost" -> config.legCrouchExitCost;
            case "legCrawlEnterCost" -> config.legCrawlEnterCost;
            case "legCrawlExitCost" -> config.legCrawlExitCost;
            case "legStaminaRegenDelayTicks" -> config.legStaminaRegenDelayTicks;
            default -> throw new IllegalStateException("Unmapped MDC field " + field.key());
        };
    }

    private static void writeField(ModClothConfig config, ModernDamageBalanceSchema.Field field, double value) {
        if (field.metric() != ModernDamageBalanceSchema.Metric.VALUE) {
            EffectEntry entry = injuryEntry(config, field);
            switch (field.metric()) {
                case THRESHOLD -> entry.threshold = (float) value;
                case CHANCE -> entry.chance = value;
                case DURATION -> entry.duration = (int) value;
                case VALUE -> throw new IllegalStateException("Unexpected scalar metric");
            }
            return;
        }
        switch (field.key()) {
            case "minorBleedingIntervalTicks" -> config.minorBleedingIntervalTicks = (int) value;
            case "minorBleedingDamagePerLevel" -> config.minorBleedingDamagePerLevel = (float) value;
            case "majorBleedingIntervalTicks" -> config.majorBleedingIntervalTicks = (int) value;
            case "majorBleedingDamagePerLevel" -> config.majorBleedingDamagePerLevel = (float) value;
            case "meleeAttackCost" -> config.meleeAttackCost = (float) value;
            case "bowDrawCostPerTick" -> config.bowDrawCostPerTick = (float) value;
            case "adsCostPerTick" -> config.adsCostPerTick = (float) value;
            case "miningCostPerBlock" -> config.miningCostPerBlock = (float) value;
            case "staminaRegenDelayTicks" -> config.staminaRegenDelayTicks = (int) value;
            case "legSprintingCostPerTick" -> config.legSprintingCostPerTick = (float) value;
            case "legSwimmingCostPerTick" -> config.legSwimmingCostPerTick = (float) value;
            case "legJumpCost" -> config.legJumpCost = (float) value;
            case "legCrouchEnterCost" -> config.legCrouchEnterCost = (float) value;
            case "legCrouchExitCost" -> config.legCrouchExitCost = (float) value;
            case "legCrawlEnterCost" -> config.legCrawlEnterCost = (float) value;
            case "legCrawlExitCost" -> config.legCrawlExitCost = (float) value;
            case "legStaminaRegenDelayTicks" -> config.legStaminaRegenDelayTicks = (int) value;
            default -> throw new IllegalStateException("Unmapped MDC field " + field.key());
        }
    }

    private static EffectEntry injuryEntry(ModClothConfig config, ModernDamageBalanceSchema.Field field) {
        ModernDamageBalanceSchema.InjuryProfile profile = ModernDamageBalanceSchema.injuryProfiles().stream()
                .filter(candidate -> field.key().startsWith(candidate.prefix()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown MDC injury profile " + field.key()));
        ModClothConfig.BodyPartConfig part = config.bodyParts.get(profile.partKey());
        if (part == null) throw new IllegalStateException("Missing MDC body part config " + profile.partKey());
        List<EffectEntry> matches = part.generic.stream()
                .filter(entry -> profile.effectId().equals(entry.effectId))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected one MDC effect " + profile.effectId()
                    + " for " + profile.partKey() + ", got " + matches.size());
        }
        return matches.get(0);
    }
}
