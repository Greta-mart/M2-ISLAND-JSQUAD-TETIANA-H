package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

/** Орел - найшвидший хижак (швидкість 3), полює дрібну і слабку здобич. */
public class Eagle extends Predator {

    public Eagle(Species species, Island island) {
        super(species, island);
    }
}
