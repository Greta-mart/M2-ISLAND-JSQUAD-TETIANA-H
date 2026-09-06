package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

/** Кінь - велике швидке травоїдне, тікає від хижаків найкраще з великих видів. */
public class Horse extends Herbivore {

    public Horse(Species species, Island island) {
        super(species, island);
    }
}
