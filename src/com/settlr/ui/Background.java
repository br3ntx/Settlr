package com.settlr.ui;

import com.settlr.exception.SettlrException;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Loads a screen's data on a background thread, then shows it on the Swing thread.
 *
 * Swing draws the window and handles clicks on one thread, the Event Dispatch Thread (EDT).
 * If a screen asked MySQL for its data on that thread, the whole window would freeze until the
 * database answered. So each screen splits its work in two:
 *   1. load: call the services (database work), in the background via SwingWorker;
 *   2. show: build the components from what was loaded, back on the EDT (Swing isn't thread-safe).
 *
 *     background.load(() -> service.getWeek(day),      // 1. background thread
 *                     week -> showWeek(week),           // 2. EDT
 *                     error -> showMessage(error.getMessage(), true));
 *
 * Each screen has its own Background. If it is asked to load again before the last load finished
 * (e.g. the user clicks the tab twice), only the newest result is shown.
 */
public final class Background {

    /** Work that reads from the services. Runs on the background thread, so it must not touch Swing. */
    @FunctionalInterface
    public interface Load<T> {
        /** Does the loading and gives back what was loaded. */
        T load() throws SettlrException;
    }

    /**
     * What one part of a screen's data turned out to be: the value, or the error that stopped it.
     * Lets a screen load several parts at once and still show the parts that worked.
     */
    public static final class Result<T> {
        private final T value;
        private final SettlrException error;

        private Result(T value, SettlrException error) {
            this.value = value;
            this.error = error;
        }

        /** Runs the load now (call it from inside a background load) and keeps its value or its error. */
        public static <T> Result<T> of(Load<T> load) {
            try {
                return new Result<>(load.load(), null);
            } catch (SettlrException e) {
                return new Result<>(null, e);
            }
        }

        /** The value, or the original error thrown again so the screen can show its message. */
        public T get() throws SettlrException {
            if (error != null) {
                throw error;
            }
            return value;
        }
    }

    private int latest; // only touched on the EDT

    /** Loads in the background, then calls show (or failed) on the EDT. Call this from the EDT. */
    public <T> void load(Load<T> work, Consumer<T> show, Consumer<SettlrException> failed) {
        int ticket = ++latest;
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.load();
            }

            @Override
            protected void done() {
                if (ticket != latest) {
                    return; // a newer load started after this one: its result wins
                }
                try {
                    show.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof SettlrException) {
                        failed.accept((SettlrException) cause);
                    } else {
                        cause.printStackTrace(); // a bug, not a user mistake: print it for whoever is debugging
                        failed.accept(new SettlrException("Something went wrong while loading. Please try again.", cause));
                    }
                }
            }
        }.execute();
    }
}
