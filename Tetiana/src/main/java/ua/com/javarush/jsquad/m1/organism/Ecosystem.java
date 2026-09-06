package ua.com.javarush.jsquad.m1.organism;

import ua.com.javarush.jsquad.m1.island.Island;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Хто живе на острові: список видів, їхні характеристики і меню.
 * <p>
 * Замінює колишній enum Species. Види тепер - звичайні обʼєкти, тому можна зібрати
 * скільки завгодно різних екосистем і передати потрібну в Settings.
 * <p>
 * Види оголошуємо ЗНИЗУ ВГОРУ харчовим ланцюгом (рослини -> травоїдні -> хижаки):
 * тоді меню посилається лише на вже створені види, і кільце "А їсть Б, Б їсть А" неможливе.
 */
public final class Ecosystem {

    private final List<Species> all = new ArrayList<>();
    private final List<Species> animals = new ArrayList<>();
    private final List<Species> plants = new ArrayList<>();
    private List<Species> byImportance = List.of();

    /**
     * Повна екосистема з ТЗ до модуля 2: 5 хижаків + 10 травоїдних + 2 рослини.
     * <p>
     * Вага, максимум на клітинку, швидкість і "повний обід" узяті з таблиці характеристик
     * у завданні. Вірогідності полювання для пар Вовк/Удав/Лисиця/Ведмідь/Орел проти
     * Коня/Оленя/Кролика узяті з таблиці "хто кого їсть" у завданні буквально; таблиця
     * в джерелі обривається на стовпці "Кролик", тому вірогідності для решти можливої
     * здобичі (Миша, Коза, Вівця, Кабан, Буйвол, Качка, Гусінь) дописані самостійно
     * за тією ж логікою (менша й слабша здобич - вищий шанс, надто велика для хижака - 0%).
     * Кількість дитинчат і стартова кількість особин у завданні не задані - підібрані так,
     * щоб острів не вимирав за перші ж такти.
     */
    public static Ecosystem standard() {
        Ecosystem e = new Ecosystem();

        //                          назва      іконка   вага   макс  швидк  обід   дітей  старт
        Species grass  = e.plant  ("Трава",   "🌿",     1.0,   200,                       3200);
        Species grain  = e.plant  ("Зерно",   "🌾",     0.5,   200,                       1600);

        // ---- травоїдні: знизу вгору, спершу ті, що їдять тільки рослини ----
        Species gusin  = e.animal("Гусінь",  "🐛",    0.01,  1000,   0,   0.00,    3,   3000, Caterpillar::new);
        Species mouse  = e.animal("Миша",    "🐁",    0.05,   500,   1,   0.01,    6,   1200, Mouse::new);
        Species rabbit = e.animal("Кролик",  "🐇",    2.0,    150,   2,   0.45,    4,   1600, Rabbit::new);
        Species horse  = e.animal("Кінь",    "🐎",  400.0,     20,   4,  60.00,    1,     80, Horse::new);
        Species deer   = e.animal("Олень",   "🦌",  300.0,     20,   4,  50.00,    1,    150, Deer::new);
        Species goat   = e.animal("Коза",    "🐐",   60.0,    140,   3,  10.00,    2,    300, Goat::new);
        Species sheep  = e.animal("Вівця",   "🐑",   70.0,    140,   3,  15.00,    2,    300, Sheep::new);
        Species boar   = e.animal("Кабан",   "🐗",  400.0,     50,   2,  50.00,    2,    200, Boar::new);
        Species buffal = e.animal("Буйвол",  "🐃",  700.0,     10,   3, 100.00,    1,     60, Buffalo::new);
        Species duck   = e.animal("Качка",   "🦆",    1.0,    200,   4,   0.15,    3,    800, Duck::new);

        // трави та зерна травоїдним вистачає всім - у кожного своя частка, щоб не витісняли одне одного
        // гусінь (fullMeal = 0) за таблицею ТЗ нічого не їсть і не голодує - меню їй не потрібне
        mouse.eats(grain, 100);
        rabbit.eats(grass, 100);
        horse.eats(grass, 100);
        deer.eats(grass, 100);
        goat.eats(grass, 100);
        sheep.eats(grass, 100);
        boar.eats(grass, 100);
        buffal.eats(grass, 100);
        duck.eats(grain, 100).eats(gusin, 50);          // виняток з ТЗ: травоїдна качка їсть гусінь

        // ---- хижаки: знизу вгору харчовим ланцюгом ----
        Species fox   = e.animal("Лисиця", "🦊",   8.0,   30,   2,   2.00,   2,    200, Fox::new);
        fox.eats(rabbit, 70).eats(mouse, 80).eats(duck, 40).eats(gusin, 70);

        Species eagle = e.animal("Орел",   "🦅",   6.0,   20,   3,   1.00,   2,    100, Eagle::new);
        eagle.eats(rabbit, 90).eats(mouse, 95).eats(duck, 60).eats(gusin, 80).eats(fox, 10);

        Species boa   = e.animal("Удав",   "🐍",  15.0,   30,   1,   3.00,   2,    150, Boa::new);
        boa.eats(fox, 15).eats(rabbit, 20).eats(mouse, 50).eats(duck, 25).eats(gusin, 60);

        Species wolf  = e.animal("Вовк",   "🐺",  50.0,   30,   3,   8.00,   2,    150, Wolf::new);
        wolf.eats(horse, 10).eats(deer, 15).eats(rabbit, 60).eats(mouse, 40)
            .eats(goat, 20).eats(sheep, 20).eats(boar, 5).eats(duck, 30).eats(gusin, 50);

        Species bear  = e.animal("Ведмідь", "🐻", 500.0,    5,   2,  80.00,   1,     20, Bear::new);
        bear.eats(boa, 80).eats(fox, 50).eats(horse, 40).eats(deer, 80).eats(rabbit, 80)
            .eats(boar, 30).eats(goat, 60).eats(sheep, 60).eats(buffal, 10)
            .eats(mouse, 20).eats(duck, 20).eats(gusin, 30);

        return e;
    }

    public Species plant(String title, String icon, double weight, int maxPerCell, int startCount) {
        return register(new Species(all.size(), title, icon, weight, maxPerCell,
                0, 0, 0, startCount, false, Plant::new));
    }

    public Species animal(String title, String icon, double weight, int maxPerCell, int speed,
                          double fullMeal, int cubs, int startCount,
                          BiFunction<Species, Island, Organism> factory) {
        return register(new Species(all.size(), title, icon, weight, maxPerCell,
                speed, fullMeal, cubs, startCount, true, factory));
    }

    private Species register(Species species) {
        all.add(species);
        (species.isAnimal() ? animals : plants).add(species);
        List<Species> reversed = new ArrayList<>(all);
        Collections.reverse(reversed);
        byImportance = List.copyOf(reversed);
        return species;
    }

    /** Усі види в порядку оголошення. */
    public List<Species> all() { return all; }

    /** Тільки тварини - рослини не ходять і не їдять, щоб не перебирати їх щотакту. */
    public List<Species> animals() { return animals; }

    /** Тільки рослини - вони ростуть самі, скільки б їх видів не було. */
    public List<Species> plants() { return plants; }

    /** Для карти: спершу верхівка харчового ланцюга (оголошений останнім). */
    public List<Species> byImportance() { return byImportance; }

    /** Скільки різних видів - стільки комірок у кожній клітинці. */
    public int size() { return all.size(); }
}
