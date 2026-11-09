package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidCustomizationTest {
    @TempDir Path dir;
    @Test void createsEditableCatalogWithoutOverwritingCustomTextAndFallsBackForMissingKeys() throws Exception {
        Path path = dir.resolve("messages.json");
        var defaults = RaidMessages.load(path);
        assertEquals("Faltam 5min para a próxima raid.", RaidMessages.render(defaults,"raid.aviso","tempo","5min"));
        Files.writeString(path,"{\"raid.retorno\":\"Você voltou!\",\"raid.spawn\":\"Boss {boss}: {boss}\"}");
        var customized = RaidMessages.load(path);
        assertEquals("Você voltou!", RaidMessages.render(customized,"raid.retorno"));
        assertEquals("Boss Mewtwo: Mewtwo", RaidMessages.render(customized,"raid.spawn","boss","Mewtwo"));
        assertEquals(defaults.get("comandos.bloqueados"),customized.get("comandos.bloqueados"));
        RaidMessages.load(path);
        assertEquals("Você voltou!", RaidMessages.render(RaidMessages.load(path),"raid.retorno"));
    }
    @Test void substitutionsAreLiteralAndSinglePass() throws Exception {
        var values = RaidMessages.load(dir.resolve("messages.json"));
        assertEquals("$5\\{tempo} apareceu! Use /raid para abrir o menu e entrar na arena.",
            RaidMessages.render(values,"raid.spawn","boss","$5\\{tempo}","tempo","não inserir"));
        assertThrows(IllegalArgumentException.class,()->RaidMessages.render(values,"raid.spawn","boss"));
    }
    @Test void invalidCatalogIsRejectedWithoutReplacingActiveMessages() throws Exception {
        String previous = RaidMessages.msg("raid.retorno");
        Path path=dir.resolve("messages.json");
        Files.writeString(path,"{\"raid.retorno\":42}");
        assertThrows(java.io.IOException.class,()->RaidMessages.load(path));
        assertEquals(previous,RaidMessages.msg("raid.retorno"));
        Files.writeString(path,"{invalid");
        assertThrows(java.io.IOException.class,()->RaidMessages.load(path));
        Files.writeString(path,"{\"raid.retorno\":null}");
        assertThrows(java.io.IOException.class,()->RaidMessages.load(path));
    }
    @Test void loadsNewBossFilesAndRandomlySelectsOnlyRegisteredBosses() throws Exception {
        var rayquaza = new RaidConfig.Boss();
        var mewtwo = new RaidConfig.Boss(); mewtwo.id="mewtwo"; mewtwo.species="mewtwo";
        JsonFiles.write(dir.resolve("rayquaza.json"),rayquaza);
        JsonFiles.write(dir.resolve("mewtwo.json"),mewtwo);
        var bosses=RaidBossCatalog.load(dir);
        assertEquals(Set.of("rayquaza","mewtwo"),bosses.keySet());
        var seen = new HashSet<String>();
        var random = new Random(42);
        for(int i=0;i<100;i++) {
            String id=RaidBossCatalog.randomId(bosses,random);
            assertTrue(bosses.containsKey(id)); seen.add(id);
        }
        assertEquals(bosses.keySet(),seen);
        assertEquals(7200,new RaidConfig().intervalSeconds);
        assertTrue(new RaidConfig().automaticRaids);
    }
    @Test void rejectsDuplicateInvalidAndEmptyBossLists() throws Exception {
        assertThrows(java.io.IOException.class,()->RaidBossCatalog.load(dir));
        JsonFiles.write(dir.resolve("one.json"),new RaidConfig.Boss());
        JsonFiles.write(dir.resolve("two.json"),new RaidConfig.Boss());
        assertThrows(java.io.IOException.class,()->RaidBossCatalog.load(dir));
        Files.delete(dir.resolve("two.json"));
        var invalid=new RaidConfig.Boss(); invalid.hp=0;
        JsonFiles.write(dir.resolve("one.json"),invalid);
        assertThrows(IllegalArgumentException.class,()->RaidBossCatalog.load(dir));
    }
}
