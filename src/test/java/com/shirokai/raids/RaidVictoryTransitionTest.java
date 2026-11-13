package com.shirokai.raids;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidVictoryTransitionTest {
    @TempDir Path dir;
    @Test void contractionIsContinuousPositiveAndDoesNotGrowTheBoss(){
        for(float original:new float[]{.5f,1f,2.5f,5f}){
            float previous=original;
            for(int age=0;age<=60;age++){float scale=RaidVictoryTransition.scaleAt(original,age);assertTrue(scale>0&&scale<=previous+.00001f);previous=scale;}
            assertEquals(original,RaidVictoryTransition.scaleAt(original,0));assertEquals(original*.45f,RaidVictoryTransition.scaleAt(original,60),.00001f);
        }
        assertTrue(RaidVictoryTransition.BURST_TICK>=20);assertTrue(RaidVictoryTransition.REVEAL_TICK-RaidVictoryTransition.BURST_TICK>=20);
    }
    @Test void installedShirokaiDefaultsUpgradeWithoutDeletingCustomizedText()throws Exception{
        var old=new LinkedHashMap<String,String>();
        old.put("chat.prefixo","&f✦ &lSHIROKAI&r &c&lRAID&r &8» &f{texto}");
        old.put("bossbar.proxima","&f&lSHIROKAI&r &8✦ &c&lRAID&r &8┃ &fPróxima em &e{tempo}");
        old.put("bossbar.titulo","&f&lSHIROKAI&r &8┃ &c{boss} &8┃ &f{vida}&7/&f{vida_maxima} &c♥ &8┃ &e{tempo}");
        old.put("captura.bossbar","&f&lSHIROKAI&r &8┃ &b&lCAPTURA&r &8┃ &b{boss} &8┃ &e{tempo} &8┃ &f{quantidade} bolas");
        old.put("captura.inicio","&eSua cópia de {boss} está brilhando na arena! &fEnvie seu Pokémon, mire na cópia e pressione R. Você tem {quantidade} bolas e 2 minutos desde a vitória. Chance por bola: {chance}%.");
        Path path=dir.resolve("messages.json");JsonFiles.write(path,old);String original=Files.readString(path);var upgraded=RaidMessages.load(path);
        for(String key:List.of("chat.prefixo","bossbar.proxima","bossbar.titulo","captura.bossbar")){assertFalse(upgraded.get(key).contains("SHIROKAI"));assertTrue(upgraded.get(key).contains("RAID"));}
        assertFalse(upgraded.get("captura.inicio").toLowerCase(Locale.ROOT).contains("chance"));assertFalse(upgraded.get("captura.inicio").contains("%"));assertTrue(upgraded.get("captura.inicio").contains("{tempo}"));
        assertEquals(original,Files.readString(path));assertEquals("Minha marca {texto}",RaidMessages.upgradeDefault("chat.prefixo","Minha marca {texto}"));
    }
}
