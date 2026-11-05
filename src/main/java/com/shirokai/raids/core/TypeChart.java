package com.shirokai.raids.core;

import java.util.*;

/** Generation VI+ basic type chart. No abilities, weather or Showdown effects. */
public final class TypeChart {
    private TypeChart() {}
    private static final Map<String, Set<String>> STRONG = parse("normal:;fire:grass,ice,bug,steel;water:fire,ground,rock;electric:water,flying;grass:water,ground,rock;ice:grass,ground,flying,dragon;fighting:normal,ice,rock,dark,steel;poison:grass,fairy;ground:fire,electric,poison,rock,steel;flying:grass,fighting,bug;psychic:fighting,poison;bug:grass,psychic,dark;rock:fire,ice,flying,bug;ghost:psychic,ghost;dragon:dragon;dark:psychic,ghost;steel:ice,rock,fairy;fairy:fighting,dragon,dark");
    private static final Map<String, Set<String>> WEAK = parse("normal:rock,steel;fire:fire,water,rock,dragon;water:water,grass,dragon;electric:electric,grass,dragon;grass:fire,grass,poison,flying,bug,dragon,steel;ice:fire,water,ice,steel;fighting:poison,flying,psychic,bug,fairy;poison:poison,ground,rock,ghost;ground:grass,bug;flying:electric,rock,steel;psychic:psychic,steel;bug:fire,fighting,poison,flying,ghost,steel,fairy;rock:fighting,ground,steel;ghost:dark;dragon:steel;dark:fighting,dark,fairy;steel:fire,water,electric,steel;fairy:fire,poison,steel");
    private static final Map<String, Set<String>> IMMUNE = parse("normal:ghost;electric:ground;fighting:ghost;poison:steel;ground:flying;psychic:dark;ghost:normal;dragon:fairy");
    private static Map<String, Set<String>> parse(String text) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String row : text.split(";")) {
            String[] pair = row.split(":", -1);
            result.put(pair[0], pair[1].isEmpty() ? Set.of() : Set.of(pair[1].split(",")));
        }
        return Map.copyOf(result);
    }
    public static double effectiveness(String attack, Iterable<String> defence) {
        double result = 1;
        for (String type : defence) {
            if (IMMUNE.getOrDefault(attack, Set.of()).contains(type)) return 0;
            if (STRONG.getOrDefault(attack, Set.of()).contains(type)) result *= 2;
            if (WEAK.getOrDefault(attack, Set.of()).contains(type)) result *= .5;
        }
        return result;
    }
}
