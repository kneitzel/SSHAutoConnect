package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.config.*;
import de.kneitzel.model.*;
import de.kneitzel.security.*;
import de.kneitzel.ui.*;
import de.neitzel.fx.tray.TraySupport;

import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

class AppLifecycleTest {
    @TempDir Path temp;

    @BeforeAll
    static void toolkit() throws Exception {
        ComponentTest.toolkit();
    }

    private SSHAutoConnect app;
    private Stage stage;

    @AfterEach
    void stop() {
        Fx.call(
                () -> {
                    if (app != null) app.stop();
                    for (Window window : List.copyOf(Window.getWindows())) window.hide();
                    return null;
                });
    }

    private ConfigurationRepository start(boolean trayOnly) throws Exception {
        ConfigurationRepository repo = new ConfigurationRepository(temp.resolve("config.json"));
        AppConfig config = RepositoryTest.config();
        config.setStartInTray(trayOnly);
        repo.save(config);
        TraySupport.Backend backend =
                (image, label, open, quit) -> CompletableFuture.completedFuture(null);
        app =
                new SSHAutoConnect(
                        repo,
                        new CredentialProtection(new CredentialProtectionTest.Store()),
                        temp.resolve("no-legacy"),
                        (t, c) -> new CoordinatorTest.Fake(),
                        backend);
        Fx.call(
                () -> {
                    stage = new Stage();
                    app.start(stage);
                    return null;
                });
        waitUntil(() -> Fx.call(() -> !stage.getScene().getRoot().isDisable()));
        return repo;
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(25);
        assertTrue(condition.getAsBoolean());
    }

    private static Button button(Stage window, String name) {
        window.getScene().getRoot().applyCss();
        window.getScene().getRoot().layout();
        return window.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && b.getText().equals(name))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow();
    }

    private static Stage editor() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage s && "Edit SSH profile".equals(s.getTitle()))
                .map(w -> (Stage) w)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void realApplicationOpensComponentEditorCancelsAndSavesThroughRepository() throws Exception {
        ConfigurationRepository repo = start(false);
        Fx.call(
                () -> {
                    assertTrue(stage.isShowing());
                    snapshot(stage, "main-window");
                    button(stage, "Edit").fire();
                    Stage edit = editor();
                    edit.getScene().getRoot().applyCss();
                    edit.getScene().getRoot().layout();
                    snapshot(edit, "target-editor");
                    ((TextField) edit.getScene().lookup("#hostname")).setText("cancelled-host");
                    button(edit, "Cancel").fire();
                    return null;
                });
        assertEquals(
                "example.test",
                repo.load().getProfiles().getFirst().getTargets().getFirst().getHostname());
        Fx.call(
                () -> {
                    button(stage, "Edit").fire();
                    Stage edit = editor();
                    edit.getScene().getRoot().applyCss();
                    edit.getScene().getRoot().layout();
                    ((TextField) edit.getScene().lookup("#hostname")).setText("saved-host");
                    button(edit, "Save").fire();
                    return null;
                });
        waitUntil(
                () ->
                        Fx.call(
                                () ->
                                        Window.getWindows().stream()
                                                .noneMatch(
                                                        w ->
                                                                w instanceof Stage s
                                                                        && "Edit SSH profile"
                                                                                .equals(
                                                                                        s
                                                                                                .getTitle()))));
        assertEquals(
                "saved-host",
                repo.load().getProfiles().getFirst().getTargets().getFirst().getHostname());
    }

    @Test
    void trayOnlyKeepsAccessibleWindowWhenBackendIsUnavailable() throws Exception {
        start(true);
        assertTrue(Fx.call(stage::isShowing));
    }

    private static void snapshot(Stage stage, String name) {
        try {
            WritableImage image = stage.getScene().snapshot(null);
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            java.awt.image.BufferedImage out =
                    new java.awt.image.BufferedImage(
                            width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            PixelReader pixels = image.getPixelReader();
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) out.setRGB(x, y, pixels.getArgb(x, y));
            Path file = Path.of("target/qa/" + name + ".png");
            Files.createDirectories(file.getParent());
            javax.imageio.ImageIO.write(out, "png", file.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
