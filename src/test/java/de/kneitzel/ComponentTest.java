package de.kneitzel;

import static org.junit.jupiter.api.Assertions.*;

import de.kneitzel.model.*;
import de.kneitzel.security.Credentials;
import de.kneitzel.ui.*;
import de.neitzel.fx.tray.TraySupport;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class ComponentTest {
    @BeforeAll
    static void toolkit() throws Exception {
        Assumptions.assumeTrue(
                !System.getProperty("os.name").startsWith("Linux")
                        || System.getenv("DISPLAY") != null,
                "JavaFX requires a display; run with xvfb on Linux");
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(
                    () -> {
                        Platform.setImplicitExit(false);
                        started.countDown();
                    });
        } catch (IllegalStateException e) {
            started.countDown();
        }
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @Test
    void componentsBindIsolatedDraftsAndCancelDoesNotMutatePersistedSettings() {
        Fx.call(
                () -> {
                    SshConfig a = new SshConfig();
                    a.setHostname("original");
                    SshConfig b = new SshConfig();
                    b.setHostname("other");
                    SshConfigForm first = SshConfigForm.create(), second = SshConfigForm.create();
                    first.load("First", a, Credentials.EMPTY);
                    second.load("Second", b, Credentials.EMPTY);
                    new javafx.scene.Scene(first);
                    first.applyCss();
                    first.layout();
                    ((TextField) first.lookup("#hostname")).setText("edited");
                    assertEquals("edited", first.value().getHostname());
                    assertEquals("original", a.getHostname());
                    assertEquals("other", second.value().getHostname());
                    assertTrue(first.dirty());
                    first.load("First", a, Credentials.EMPTY);
                    assertEquals("original", first.value().getHostname());
                    assertFalse(first.dirty());
                    assertThrows(
                            IllegalStateException.class, () -> first.setModel(new TargetDraft()));
                    return null;
                });
    }

    @Test
    void reusingAReleasedComponentKeepsItsBoundModelIdentity() {
        Fx.call(
                () -> {
                    SshConfigForm form = SshConfigForm.create();
                    Object model = form.getModel();
                    for (int i = 0; i < 100; i++) {
                        SshConfig t = new SshConfig();
                        t.setHostname("host-" + i);
                        form.load("Profile " + i, t, Credentials.EMPTY);
                        assertSame(model, form.getModel());
                        assertEquals("host-" + i, form.value().getHostname());
                    }
                    form.clearSecrets();
                    return null;
                });
    }

    @Test
    void trayLifecycleHasRecoverableFallbackAndConfirmedQuit() {
        Fx.call(
                () -> {
                    Stage stage = new Stage();
                    stage.show();
                    AtomicBoolean allowed = new AtomicBoolean(),
                            quit = new AtomicBoolean(),
                            removed = new AtomicBoolean();
                    TraySupport tray =
                            TraySupport.attach(
                                    stage,
                                    new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                                    "test",
                                    allowed::get,
                                    () -> quit.set(true),
                                    (image, label, open, exit) ->
                                            CompletableFuture.completedFuture(
                                                    () -> removed.set(true)));
                    stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                    assertFalse(stage.isShowing());
                    tray.requestQuit();
                    assertFalse(quit.get());
                    assertTrue(stage.isShowing());
                    allowed.set(true);
                    tray.requestQuit();
                    assertTrue(quit.get());
                    assertTrue(removed.get());
                    tray.close();
                    TraySupport fallback =
                            TraySupport.attach(
                                    stage,
                                    new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                                    "test",
                                    () -> true,
                                    () -> {},
                                    (image, label, open, exit) ->
                                            CompletableFuture.completedFuture(null));
                    stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                    assertTrue(stage.isShowing());
                    fallback.close();
                    stage.hide();
                    return null;
                });
    }
}
