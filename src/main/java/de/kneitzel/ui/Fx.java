package de.kneitzel.ui;

import javafx.application.Platform;
import javafx.scene.control.*;

import java.util.concurrent.*;
import java.util.function.Supplier;

public final class Fx {
    public static <T> T call(Supplier<T> task) {
        if (Platform.isFxApplicationThread()) return task.get();
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(task.get());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        try {
            return result.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    public static boolean confirm(String title, String message) {
        return call(
                () -> {
                    Alert a =
                            new Alert(
                                    Alert.AlertType.CONFIRMATION,
                                    message,
                                    ButtonType.YES,
                                    ButtonType.NO);
                    a.setTitle(title);
                    a.setHeaderText(null);
                    return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
                });
    }

    public static void error(String message) {
        Platform.runLater(
                () -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
                    a.setHeaderText("SSH Auto Connect");
                    a.show();
                });
    }

    public static String message(Throwable e) {
        while (e.getCause() != null
                && (e instanceof CompletionException || e instanceof ExecutionException))
            e = e.getCause();
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private Fx() {}
}
