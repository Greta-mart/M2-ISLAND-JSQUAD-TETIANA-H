package ua.com.javarush.jsquad.m1.island;

import ua.com.javarush.jsquad.m1.config.Settings;

import java.util.concurrent.CountDownLatch;

/**
 * Крутить такти, поки не спрацює умова зупинки з налаштувань.
 * <p>
 * Сама симуляція виконується асинхронно (див. {@link Island#startAsync}): три завдання
 * такту (рослини / тварини / статистика) крутяться в scheduled-пулі острова. Головний потік
 * тут лише чекає на {@link CountDownLatch}, який відкриває завдання статистики, щойно
 * настає умова зупинки.
 */
public class Simulation {

    private final Settings settings;
    private final View view;
    private final Island island;

    public Simulation(Settings settings, View view) {
        this.settings = settings;
        this.view = view;
        this.island = new Island(settings);
    }

    public void run() {
        CountDownLatch finished = new CountDownLatch(1);
        island.startAsync(() -> {
            view.show(island);
            String reason = stopReason();
            if (reason != null) {
                System.out.println("Симуляція завершена: " + reason);
                island.stopAsync();
                finished.countDown();
            }
        });
        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Island island() {
        return island;
    }

    private String stopReason() {
        if (settings.stopWhenNoAnimals() && island.totalAnimals() == 0) return "усі тварини загинули";
        if (settings.maxTicks() > 0 && island.tickNumber() >= settings.maxTicks()) {
            return "минуло " + settings.maxTicks() + " тактів";
        }
        return null;
    }
}
