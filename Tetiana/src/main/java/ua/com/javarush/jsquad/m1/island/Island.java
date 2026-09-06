package ua.com.javarush.jsquad.m1.island;

import ua.com.javarush.jsquad.m1.config.Settings;
import ua.com.javarush.jsquad.m1.organism.Animal;
import ua.com.javarush.jsquad.m1.organism.Ecosystem;
import ua.com.javarush.jsquad.m1.organism.Species;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Острів: сітка клітинок, свої правила, свій кубик і своя статистика.
 * <p>
 * <b>Багатопотоковість (ТЗ модуля 2).</b> Один такт складається із трьох завдань:
 * ріст рослин -> життєвий цикл тварин -> виведення статистики. Усі три виконуються
 * за розкладом в одному {@link ScheduledExecutorService} ("scheduled пул", по одному
 * завданню на кожну фазу такту). Кожне завдання перевіряє поточну фазу {@link #phase}
 * і працює, лише коли настала його черга - інакше миттєво завершується і чекає
 * наступного запуску за розкладом. Це і дає послідовність "рослини -> тварини ->
 * статистика -> знову рослини", хоча самі три завдання - незалежні задачі одного пулу,
 * як і вимагає завдання.
 * <p>
 * Усередині фази життєвого циклу клітинки острова діляться між робочими потоками
 * ЗВИЧАЙНОГО пулу {@link #lifecyclePool} - кожен потік послідовно обробляє свою частку
 * клітинок. Через те що тварина може перейти в клітинку, яку обробляє інший потік,
 * усі операції над клітинкою ({@link Cell}) синхронізовані, а перехід
 * ({@link Animal#move()}) бере лок обох клітинок одразу.
 */
public final class Island {

    private enum Phase { PLANTS, PLANTS_BUSY, ANIMALS, ANIMALS_BUSY, STATS, STATS_BUSY }

    private final Settings settings;
    private final Ecosystem ecosystem;
    private final Dice dice;
    private final Statistics statistics = new Statistics();

    private final Cell[][] cells;
    private final List<Cell> order;                  // порядок обходу клітинок, тасується щотакту

    private final Object phaseLock = new Object();
    private volatile Phase phase = Phase.PLANTS;

    private ScheduledExecutorService scheduler;       // "scheduled пул": рослини / тварини / статистика
    private ExecutorService lifecyclePool;            // звичайний пул усередині завдання життєвого циклу
    private int lifecyclePoolSize = 1;

    private volatile int tickNumber;
    private volatile long tickStartedAt;
    private volatile long nextTickAllowedAt;          // settings.tickMillis() - пауза між тактами (як і раніше)

    public Island(Settings settings) {
        this.settings = settings;
        this.ecosystem = settings.ecosystem();
        this.dice = Dice.forSeed(settings.seed());
        this.cells = new Cell[settings.rows()][settings.cols()];
        this.order = new ArrayList<>(settings.rows() * settings.cols());
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                cells[row][col] = new Cell(row, col, ecosystem);
                order.add(cells[row][col]);
            }
        }
        for (Species species : ecosystem.all()) settle(species);
    }

    // ==================== одноразовий (синхронний) такт - для тестів і простих запусків ====================

    /** Один такт без потоків: виросли рослини -> кожна тварина прожила свій хід. Зручно для тестів. */
    public void tick() {
        long started = System.nanoTime();
        tickNumber++;
        statistics.reset();
        growPlants();
        liveOneTickSequential();
        statistics.tookNanos(System.nanoTime() - started);
    }

    // ==================== багатопотокова симуляція ====================

    /**
     * Запускає нескінченний конвеєр із трьох завдань у {@link ScheduledExecutorService}.
     * {@code onStatsPhase} викликається щотакту одразу після друку/збору статистики -
     * саме там {@link ua.com.javarush.jsquad.m1.island.Simulation} перевіряє умову зупинки.
     */
    public void startAsync(Runnable onStatsPhase) {
        this.lifecyclePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.lifecyclePool = Executors.newFixedThreadPool(lifecyclePoolSize);
        this.scheduler = Executors.newScheduledThreadPool(3);

        // Період перевірки фази - частка такту: досить малий, щоб наступна фаза підхопилась
        // майже одразу після завершення попередньої, і досить великий, щоб не молотити марно.
        long checkPeriod = Math.max(5, settings.tickMillis() / 5);

        scheduler.scheduleWithFixedDelay(this::growPlantsTask, 0, checkPeriod, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::animalsTask, 0, checkPeriod, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> statsTask(onStatsPhase), 0, checkPeriod, TimeUnit.MILLISECONDS);
    }

    public void stopAsync() {
        if (scheduler != null) scheduler.shutdownNow();
        if (lifecyclePool != null) lifecyclePool.shutdownNow();
    }

    /**
     * Завдання 1 (scheduled пул): ріст рослин.
     * <p>
     * Тут же й пауза між тактами ({@code settings.tickMillis()}) - завдання просто нічого
     * не робить, поки не настав час, і чекає наступного запуску за розкладом.
     */
    private void growPlantsTask() {
        synchronized (phaseLock) {
            if (phase != Phase.PLANTS) return;
            if (System.currentTimeMillis() < nextTickAllowedAt) return;   // ще не час
            phase = Phase.PLANTS_BUSY;
        }
        tickStartedAt = System.nanoTime();
        tickNumber++;
        statistics.reset();
        growPlants();
        nextTickAllowedAt = System.currentTimeMillis() + settings.tickMillis();
        synchronized (phaseLock) { phase = Phase.ANIMALS; }
    }

    /** Завдання 2 (scheduled пул): життєвий цикл тварин - усередині свій, звичайний пул потоків. */
    private void animalsTask() {
        synchronized (phaseLock) {
            if (phase != Phase.ANIMALS) return;
            phase = Phase.ANIMALS_BUSY;
        }
        liveOneTickParallel();
        synchronized (phaseLock) { phase = Phase.STATS; }
    }

    /** Завдання 3 (scheduled пул): статистика по системі. */
    private void statsTask(Runnable onStatsPhase) {
        synchronized (phaseLock) {
            if (phase != Phase.STATS) return;
            phase = Phase.STATS_BUSY;
        }
        statistics.tookNanos(System.nanoTime() - tickStartedAt);
        try {
            onStatsPhase.run();
        } finally {
            synchronized (phaseLock) { phase = Phase.PLANTS; }
        }
    }

    /**
     * Обходимо клітинки у випадковому порядку - жодного списку "всі тварини острова".
     * Тварина, яка перейшла в ще не оброблену клітинку, могла б сходити двічі:
     * від цього рятує позначка про такт.
     * <p>
     * Тварин у клітинці теж тасуємо. Без цього вони ходили б групами за видами: миші завжди
     * першими виїдали б траву в кролика з-під носа, а вовк ходив би останнім - коли здобич
     * уже розбіглася. Заміряно: без тасування вовк і кролик вимирають до 20-го такту.
     */
    private void liveOneTickSequential() {
        dice.shuffle(order);
        List<Animal> buffer = new ArrayList<>(64);
        for (Cell cell : order) {
            processCell(cell, buffer);
        }
    }

    /**
     * Те саме, що {@link #liveOneTickSequential()}, але клітинки діляться між робочими
     * потоками {@link #lifecyclePool}. Кожен потік має свій буфер (список тварин клітинки) -
     * буфери потоки між собою не ділять, тому жодних гонок на самому буфері немає.
     * Гонки можливі лише на СПІЛЬНИХ клітинках (перехід тварини з чужої ділянки) -
     * від них рятує синхронізація в {@link Cell} і {@link Animal#move()}.
     */
    private void liveOneTickParallel() {
        dice.shuffle(order);
        List<List<Cell>> chunks = partition(order, lifecyclePoolSize);

        List<Callable<Void>> tasks = new ArrayList<>(chunks.size());
        for (List<Cell> chunk : chunks) {
            tasks.add(() -> {
                List<Animal> buffer = new ArrayList<>(64);      // свій буфер на потік
                for (Cell cell : chunk) {
                    processCell(cell, buffer);
                }
                return null;
            });
        }
        try {
            lifecyclePool.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void processCell(Cell cell, List<Animal> buffer) {
        buffer.clear();
        cell.collectAnimals(ecosystem, buffer);
        dice.shuffle(buffer);                    // інакше види ходили б завжди в одному порядку
        int currentTick = tickNumber;
        for (Animal animal : buffer) {
            if (!animal.isAlive() || animal.hasLivedIn(currentTick)) continue;
            animal.markLived(currentTick);
            animal.liveOneTick();
        }
    }

    private static List<List<Cell>> partition(List<Cell> cells, int parts) {
        List<List<Cell>> chunks = new ArrayList<>(parts);
        int size = cells.size();
        int base = size / parts;
        int extra = size % parts;
        int from = 0;
        for (int i = 0; i < parts && from < size; i++) {
            int chunkSize = base + (i < extra ? 1 : 0);
            int to = Math.min(size, from + chunkSize);
            if (to > from) chunks.add(cells.subList(from, to));
            from = to;
        }
        return chunks;
    }

    /** Ростуть усі види рослин, скільки б їх не було в екосистемі. */
    private void growPlants() {
        int perTick = settings.plantsPerTick();
        List<Species> plants = ecosystem.plants();
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                for (int p = 0; p < plants.size(); p++) {
                    Species plant = plants.get(p);
                    for (int i = 0; i < perTick && cell.hasRoom(plant); i++) {
                        cell.add(plant.create(this));
                    }
                }
            }
        }
    }

    /** Розселити вид на старті у випадкові клітинки. */
    private void settle(Species species) {
        for (int i = 0; i < species.startCount(); i++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                Cell cell = cells[dice.next(rows())][dice.next(cols())];
                if (cell.hasRoom(species)) {
                    cell.add(species.create(this));
                    break;
                }
            }
        }
    }

    /** Сусідня клітинка або null, якщо там уже море. */
    public Cell neighbour(Cell from, Direction direction) {
        int row = from.row() + direction.dRow();
        int col = from.col() + direction.dCol();
        boolean outside = row < 0 || row >= rows() || col < 0 || col >= cols();
        return outside ? null : cells[row][col];
    }

    public int population(Species species) {
        int total = 0;
        for (Cell[] row : cells) {
            for (Cell cell : row) total += cell.count(species);
        }
        return total;
    }

    public int totalAnimals() {
        int total = 0;
        for (Species species : ecosystem.animals()) total += population(species);
        return total;
    }

    public Settings settings() { return settings; }
    public Ecosystem ecosystem() { return ecosystem; }
    public Dice dice() { return dice; }
    public Statistics statistics() { return statistics; }
    public Cell[][] cells() { return cells; }
    public int rows() { return cells.length; }
    public int cols() { return cells[0].length; }
    public int tickNumber() { return tickNumber; }
}
