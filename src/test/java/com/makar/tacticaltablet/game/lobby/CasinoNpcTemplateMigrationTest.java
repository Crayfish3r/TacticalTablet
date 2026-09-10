package com.makar.tacticaltablet.game.lobby;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class CasinoNpcTemplateMigrationTest {
    @Test void filterIsIdempotentAndPreservesBlocksAndOrdinaryEntities() throws Exception {
        try (var input = Files.newInputStream(Path.of("src/main/resources/data/lobby/structures/spawn.nbt"))) {
            CompoundTag source = NbtIo.readCompressed(input);
            CompoundTag filtered = CasinoNpcTemplateMigration.withoutLegacyCasinoNpcs(source);
            assertEquals(16, source.getList("entities", 10).size());
            assertEquals(12, filtered.getList("entities", 10).size());
            assertEquals(source.get("blocks"), filtered.get("blocks"));
            assertEquals(source.get("palette"), filtered.get("palette"));
            assertEquals(filtered, CasinoNpcTemplateMigration.withoutLegacyCasinoNpcs(filtered));
        }
    }
    @Test void ordinaryNamedVillagerAndMachineBlockRemain() {
        CompoundTag source = new CompoundTag();
        CompoundTag villager = new CompoundTag();
        villager.putString("id", "minecraft:villager");
        villager.putString("CustomName", "{\"text\":\"Trader\"}");
        CompoundTag entry = new CompoundTag(); entry.put("nbt", villager);
        ListTag entities = new ListTag(); entities.add(entry); source.put("entities", entities);
        CompoundTag machine = new CompoundTag(); machine.putString("Name", "tacticaltablet:casino_machine");
        ListTag palette = new ListTag(); palette.add(machine); source.put("palette", palette);
        assertEquals(source, CasinoNpcTemplateMigration.withoutLegacyCasinoNpcs(source));
    }
}
