package ua.com.javarush.jsquad.m1.island;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Що сталося на острові за такт. Сама себе й друкує (див. toString).
 * <p>
 * Лічильники - {@link AtomicInteger}, бо {@link #registerBirth()}, {@link #registerEaten()}
 * і {@link #registerStarved()} тепер викликаються з кількох потоків одночасно: завдання
 * життєвого циклу тварин обробляє клітинки паралельно (див. {@link Island}).
 */
public final class Statistics {

    private final AtomicInteger born = new AtomicInteger();
    private final AtomicInteger eaten = new AtomicInteger();
    private final AtomicInteger starved = new AtomicInteger();
    private final AtomicLong nanos = new AtomicLong();

    public void reset() {
        born.set(0);
        eaten.set(0);
        starved.set(0);
    }

    public void registerBirth() { born.incrementAndGet(); }
    public void registerEaten() { eaten.incrementAndGet(); }
    public void registerStarved() { starved.incrementAndGet(); }
    public void tookNanos(long value) { nanos.set(value); }

    public int born() { return born.get(); }
    public int eaten() { return eaten.get(); }
    public int starved() { return starved.get(); }
    public double millis() { return nanos.get() / 1_000_000.0; }

    @Override
    public String toString() {
        return String.format("народилось: %d | зʼїдено: %d | з голоду: %d | такт: %.1f мс",
                born(), eaten(), starved(), millis());
    }
}
