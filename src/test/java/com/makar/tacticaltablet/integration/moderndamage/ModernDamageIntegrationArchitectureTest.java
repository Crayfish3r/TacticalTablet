package com.makar.tacticaltablet.integration.moderndamage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernDamageIntegrationArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");
    private static final Path INTEGRATION = MAIN.resolve("integration/moderndamage");

    @Test
    void directMdcReferencesStayInsideTheVersionedAdapterPackage() throws IOException {
        try (var files = Files.walk(MAIN)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.startsWith(INTEGRATION)) continue;
                assertFalse(Files.readString(file).contains("com.moderndamage.control"), file.toString());
            }
        }
    }

    @Test
    void versionAndPhysicalSideChecksPrecedeAdapterAndClientLoading() throws IOException {
        String gate = Files.readString(INTEGRATION.resolve("ModernDamageIntegration.java"));
        assertTrue(gate.contains("SUPPORTED_VERSION = \"1.0.32\""));
        assertTrue(gate.indexOf("ModList.get().isLoaded(MOD_ID)") < gate.indexOf("new ModernDamageAdapterV1032()"));
        assertTrue(gate.indexOf("SUPPORTED_VERSION.equals(detected)") < gate.indexOf("new ModernDamageAdapterV1032()"));
        assertTrue(gate.contains("DistExecutor.safeRunWhenOn(Dist.CLIENT"));

        try (var files = Files.walk(INTEGRATION.resolve("client"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(file).contains("@Mod.EventBusSubscriber"), file.toString());
            }
        }
    }

    @Test
    void serverUpdateChecksRateLimitPermissionShapeAndRevisionBeforeSaving() throws IOException {
        String packet = Files.readString(INTEGRATION.resolve("net/MdcBalanceUpdatePacket.java"));
        assertTrue(packet.contains("PacketHandler.allowC2S(player, PacketHandler.C2SAction.ADMIN_MDC)"));
        assertTrue(packet.contains("player.hasPermissions(2)"));
        assertTrue(packet.contains("ModernDamageBalanceSchema.byId(field.id()).isEmpty()"));
        assertTrue(packet.contains("submitted.put(field.id(), field.value()) != null"));
        String integration = Files.readString(INTEGRATION.resolve("ModernDamageIntegration.java"));
        assertTrue(integration.indexOf("expectedRevision != revision")
                < integration.indexOf("adapter.applyBalance(validation.values())"));
        assertTrue(integration.indexOf("ModernDamageBalanceSchema.validate(submitted)")
                < integration.indexOf("adapter.applyBalance(validation.values())"));
    }

    @Test
    void stockHudIsDisabledThroughMdcConfigAndSavingUsesAutoConfig() throws IOException {
        String client = Files.readString(INTEGRATION.resolve("client/ModernDamageClientAccessV1032.java"));
        assertTrue(client.contains("config.enableStaminaHUD = false"));
        assertTrue(client.contains("AutoConfig.getConfigHolder(ModClothConfig.class).save()"));
        String server = Files.readString(INTEGRATION.resolve("ModernDamageAdapterV1032.java"));
        assertTrue(server.contains("AutoConfig.getConfigHolder(ModClothConfig.class).save()"));
        assertTrue(server.contains("AtomicFileStore"));
        assertTrue(server.contains("moderndamage.json5.tacticaltablet.bak"));
    }
}
