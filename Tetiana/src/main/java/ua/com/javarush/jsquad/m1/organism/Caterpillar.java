package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

/**
 * Гусінь - за таблицею ТЗ: fullMeal = 0 і speed = 0, тому нічого не їсть (не голодує,
 * див. {@link Animal} - там уже є перевірка "fullMeal == 0"), не рухається (цикл на 0 кроків
 * у {@link Animal#move()}), і живе, лише розмножуючись і чекаючи, поки хтось її зʼїсть.
 */
public class Caterpillar extends Herbivore {

    public Caterpillar(Species species, Island island) {
        super(species, island);
    }
}
