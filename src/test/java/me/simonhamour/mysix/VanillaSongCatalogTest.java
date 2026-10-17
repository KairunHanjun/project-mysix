package me.simonhamour.mysix;

import static org.junit.jupiter.api.Assertions.*;
import me.simonhamour.mysix.music.VanillaSongCatalog;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VanillaSongCatalogTest {
    @ParameterizedTest
    @CsvSource({
        "C418 - Cat,cat", "c418-cat,cat", "  C418  —  CAT  ,cat",
        "C418 - 13,13", "C418 - 11,11", "C418 - blocks,blocks",
        "C418 - chirp,chirp", "C418 - far,far", "C418 - mall,mall",
        "C418 - mellohi,mellohi", "C418 - stal,stal", "C418 - strad,strad",
        "C418 - ward,ward", "C418 - wait,wait",
        "Lena Raine - Pigstep,pigstep", "Lena Raine - otherside,otherside",
        "Lena Raine - Creator,creator", "Lena Raine - Creator (Music Box),creator_music_box",
        "Samuel Åberg - 5,5", "Aaron Cherof - Relic,relic",
        "Aaron Cherof - Precipice,precipice", "Amos Roddy - Tears,tears",
        "Hyper Potions - Lava Chicken,lava_chicken", "fingerspit - Bounce,bounce"
    })
    void matchesVanillaRecordingNames(String name, String path) {
        assertEquals("minecraft:" + path, VanillaSongCatalog.find(name).orElseThrow().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Cat", "Lena Raine - Cat", "C418 - Pigstep",
            "C418 - Cat Live", "C418 - Cat karaoke", "C418 - Catt",
            "Unknown - Song", "C418 - Creator", "Lena Raine - Creator Music Box"})
    void refusesUnknownOrDifferentRecordingNames(String name) {
        assertTrue(VanillaSongCatalog.find(name).isEmpty());
    }
}
