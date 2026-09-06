package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

/** Ведмідь - найважчий хижак острова, їсть майже всіх, кому не втекти. */
public class Bear extends Predator {

    public Bear(Species species, Island island) {
        super(species, island);
    }
}
