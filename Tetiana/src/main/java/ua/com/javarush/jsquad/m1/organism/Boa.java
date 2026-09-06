package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

/** Удав - повільний (швидкість 1), але добре душить здобич у своїй клітинці. */
public class Boa extends Predator {

    public Boa(Species species, Island island) {
        super(species, island);
    }
}
