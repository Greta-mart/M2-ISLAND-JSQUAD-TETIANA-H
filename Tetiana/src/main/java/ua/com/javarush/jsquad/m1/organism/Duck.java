package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

/**
 * Качка - травоїдне, але за завданням має їсти й гусінь: окремого перевизначення це
 * не вимагає, бо {@link Animal#eat()} уже бере здобич з готового меню виду ({@code Species.diet()}),
 * незалежно від того, травоїдне це чи хижак. Досить дописати гусінь у меню качки
 * в {@link Ecosystem#standard()}.
 */
public class Duck extends Herbivore {

    public Duck(Species species, Island island) {
        super(species, island);
    }
}
