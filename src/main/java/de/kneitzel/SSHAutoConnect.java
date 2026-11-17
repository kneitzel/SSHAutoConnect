package de.kneitzel;

import de.kneitzel.config.*;
import de.kneitzel.model.*;
import de.kneitzel.runtime.*;
import de.kneitzel.security.*;
import de.kneitzel.ui.*;
import de.neitzel.fx.tray.TraySupport;

import javafx.application.*;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** JavaFX application; persisted settings, editor drafts and active sessions are separate. */
public class SSHAutoConnect extends Application {
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "ssh-status");
                        t.setDaemon(true);
                        return t;
                    });
    private final CredentialProtection protection;
    private final ConfigurationRepository repository;
    private final Path legacyFile;
    private final TraySupport.Backend trayBackend;
    private final Map<String, Credentials> credentials = new HashMap<>();
    private final Map<String, TargetCoordinator> coordinators = new HashMap<>();
    private final Map<String, TargetCoordinator.Snapshot> states = new HashMap<>();
    private final Map<String, SshConfigForm> forms = new HashMap<>();
    // Reuse released editor Components to bound NeitzelLib's global model cache by peak editor
    // count.
    private final Deque<SshConfigForm> freeForms = new ArrayDeque<>();
    private Stage stage, editor;
    private Runnable editorSave, editorDiscard;
    private SshConfigForm openForm;
    private TraySupport tray;
    private AppConfig config = new AppConfig();
    private boolean recovery, migration, closing, saving, startupAttention, quitAfterSave;
    private final ListView<Profile> profiles = new ListView<>();
    private final TextArea details = new TextArea();
    private final Label status = new Label("Loading configuration…");
    private final ComboBox<NamedCommand> command = new ComboBox<>();
    private final Button connect = new Button("Connect"),
            disconnect = new Button("Disconnect"),
            retry = new Button("Retry failed setup"),
            run = new Button("Run command"),
            cancel = new Button("Cancel command");
    private final SshTransport.Factory transport;

    public SSHAutoConnect() {
        this(
                new ConfigurationRepository(ConfigurationRepository.defaultPath()),
                new CredentialProtection(new NativeKeyStore()),
                Path.of(System.getProperty("user.home"), ".sshAutoConnect-configs.json"),
                null,
                null);
    }

    SSHAutoConnect(
            ConfigurationRepository repository,
            CredentialProtection protection,
            Path legacyFile,
            SshTransport.Factory transport,
            TraySupport.Backend trayBackend) {
        this.repository = repository;
        this.protection = protection;
        this.legacyFile = legacyFile;
        this.transport =
                transport == null
                        ? JschTransport.factory(
                                Path.of(System.getProperty("user.home"), ".ssh", "known_hosts"),
                                (host, fingerprint) ->
                                        Fx.call(
                                                () -> {
                                                    startupAttention = true;
                                                    stage.show();
                                                    return Fx.confirm(
                                                            "Trust SSH server?",
                                                            host
                                                                    + "\n"
                                                                    + fingerprint
                                                                    + "\n"
                                                                    + "Trust this unknown server"
                                                                    + " and save its key?");
                                                }))
                        : transport;
        this.trayBackend = trayBackend;
    }

    @Override
    public void start(Stage primary) {
        stage = primary;
        Platform.setImplicitExit(false);
        command.setPrefWidth(260);
        command.setPromptText("Select a command");
        stage.setTitle("SSH Auto Connect");
        details.setEditable(false);
        details.setWrapText(true);
        profiles.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refresh());
        Button add = button("New profile", this::addProfile),
                edit = button("Edit", this::editSelected),
                delete = button("Delete / exclude", this::deleteSelected),
                settings = button("Application settings", this::applicationSettings),
                saveReview =
                        button(
                                "Accept migration",
                                () ->
                                        saveConfiguration(
                                                config,
                                                () -> {
                                                    migration = false;
                                                    status.setText(
                                                            "Migration accepted. Original file"
                                                                + " preserved, including plaintext"
                                                                + " passwords.");
                                                    autoConnect();
                                                })),
                quit = button("Quit", this::requestQuit);
        saveReview.setOnAction(
                e ->
                        saveConfiguration(
                                config,
                                () -> {
                                    migration = false;
                                    saveReview.setVisible(false);
                                    status.setText(
                                            "Migration accepted. Original file preserved, including"
                                                    + " plaintext passwords.");
                                    autoConnect();
                                }));
        connect.setOnAction(e -> connectSelected(false));
        disconnect.setOnAction(e -> disconnectSelected());
        retry.setOnAction(
                e -> {
                    TargetCoordinator c = selectedCoordinator();
                    if (c != null
                            && Fx.confirm(
                                    "Retry incomplete setup",
                                    "Only failed/incomplete setup will run. Interrupted commands"
                                            + " may already have had remote effects. Continue?"))
                        observe(c.retry());
                });
        run.setOnAction(
                e -> {
                    TargetCoordinator c = selectedCoordinator();
                    NamedCommand cmd = command.getValue();
                    if (c != null && cmd != null) observe(c.run(cmd.getId()));
                });
        cancel.setOnAction(
                e -> {
                    TargetCoordinator c = selectedCoordinator();
                    if (c != null) c.cancelCommand();
                });
        Button upload = button("Upload", () -> transfer(true)),
                download = button("Download", () -> transfer(false)),
                terminal = button("External terminal", this::terminal);
        Label terminalNote =
                new Label(
                        "External terminals authenticate independently and remain open after"
                                + " disconnect.");
        terminalNote.setWrapText(true);
        VBox right =
                new VBox(
                        10,
                        new HBox(6, connect, disconnect, retry),
                        new HBox(6, command, run, cancel),
                        new HBox(6, upload, download, terminal),
                        terminalNote,
                        details);
        VBox.setVgrow(details, Priority.ALWAYS);
        right.setPadding(new Insets(12));
        SplitPane split = new SplitPane(profiles, right);
        split.setDividerPositions(.27);
        BorderPane root = new BorderPane(split);
        root.setTop(new ToolBar(add, edit, delete, settings, saveReview, quit));
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(8));
        stage.setScene(new Scene(root, 1080, 680));
        root.setDisable(true);
        stage.show();
        stage.setOnCloseRequest(
                e -> {
                    e.consume();
                    requestQuit();
                });
        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                boolean exists = Files.exists(repository.path());
                                AppConfig loaded = repository.load();
                                Path legacy = legacyFile;
                                return !exists && Files.exists(legacy)
                                        ? LegacyMigration.read(legacy)
                                        : new LegacyMigration.Review(loaded, Map.of(), List.of());
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        },
                        background)
                .whenComplete(
                        (review, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing) return;
                                            root.setDisable(false);
                                            if (error != null) {
                                                recovery = true;
                                                status.setText(
                                                        "Configuration recovery required: "
                                                                + repository.path());
                                                Fx.error(Fx.message(error));
                                            } else {
                                                config = review.config();
                                                credentials.putAll(review.credentials());
                                                migration = !review.notices().isEmpty();
                                                status.setText(
                                                        migration
                                                                ? String.join(" ", review.notices())
                                                                : repository.path().toString());
                                            }
                                            profiles.setItems(
                                                    FXCollections.observableArrayList(
                                                            config.getProfiles()));
                                            saveReview.setVisible(migration);
                                            saveReview
                                                    .managedProperty()
                                                    .bind(saveReview.visibleProperty());
                                            if (!config.getProfiles().isEmpty())
                                                profiles.getSelectionModel().selectFirst();
                                            try {
                                                var image =
                                                        javax.imageio.ImageIO.read(
                                                                SSHAutoConnect.class.getResource(
                                                                        "/icon.png"));
                                                tray =
                                                        trayBackend == null
                                                                ? TraySupport.attach(
                                                                        stage,
                                                                        image,
                                                                        "SSH Auto Connect",
                                                                        () -> {
                                                                            requestQuit();
                                                                            return false;
                                                                        },
                                                                        () -> {})
                                                                : TraySupport.attach(
                                                                        stage,
                                                                        image,
                                                                        "SSH Auto Connect",
                                                                        () -> {
                                                                            requestQuit();
                                                                            return false;
                                                                        },
                                                                        () -> {},
                                                                        trayBackend);
                                                tray.availableProperty()
                                                        .addListener(
                                                                (o, a, available) -> {
                                                                    if (available
                                                                            && config
                                                                                    .isStartInTray()
                                                                            && !migration
                                                                            && !recovery
                                                                            && !closing
                                                                            && !startupAttention)
                                                                        stage.hide();
                                                                });
                                                if (tray.isAvailable()
                                                        && config.isStartInTray()
                                                        && !migration
                                                        && !recovery) stage.hide();
                                            } catch (Exception e) {
                                                stage.show();
                                                status.setText(
                                                        "Tray unavailable. Use Quit in this"
                                                                + " window.");
                                            }
                                            if (!migration && !recovery) autoConnect();
                                            refresh();
                                        }));
        poller.scheduleAtFixedRate(
                () ->
                        Platform.runLater(
                                () ->
                                        coordinators
                                                .values()
                                                .forEach(TargetCoordinator::pollConnection)),
                2,
                2,
                TimeUnit.SECONDS);
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Profile selected() {
        return profiles.getSelectionModel().getSelectedItem();
    }

    private TargetCoordinator selectedCoordinator() {
        Profile p = selected();
        return p == null ? null : coordinators.get(p.getTargets().getFirst().getId());
    }

    private void autoConnect() {
        for (Profile p : config.getProfiles())
            if (p.getTargets().getFirst().isAutoConnect()) connect(p.getTargets().getFirst(), true);
    }

    private void connectSelected(boolean automatic) {
        Profile p = selected();
        if (p != null) connect(p.getTargets().getFirst(), automatic);
    }

    private void connect(SshConfig target, boolean automatic) {
        if (recovery || migration || closing) {
            Fx.error("Resolve configuration recovery or accept migration first");
            return;
        }
        TargetCoordinator old = coordinators.get(target.getId());
        if (old != null && (old.connected() || old.busy())) return;
        if (old != null) old.close();
        TargetCoordinator c =
                new TargetCoordinator(
                        target,
                        transport,
                        snapshot ->
                                Platform.runLater(
                                        () -> {
                                            states.put(target.getId(), snapshot);
                                            refresh();
                                        }));
        coordinators.put(target.getId(), c);
        obtainCredentials(
                target,
                automatic,
                value -> {
                    if (value != null) observe(c.connect(value));
                });
    }

    private void obtainCredentials(
            SshConfig target, boolean automatic, Consumer<Credentials> ready) {
        Credentials cached = credentials.get(target.getId());
        if (cached != null) {
            ready.accept(cached);
            return;
        }
        if (target.getEncryptedCredentials() != null) {
            status.setText("Unlocking saved credentials…");
            CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return protection.decrypt(
                                            target.getId(), target.getEncryptedCredentials());
                                } catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                            },
                            background)
                    .whenComplete(
                            (value, error) ->
                                    Platform.runLater(
                                            () -> {
                                                if (error == null) {
                                                    credentials.put(target.getId(), value);
                                                    ready.accept(value);
                                                } else {
                                                    startupAttention = true;
                                                    stage.show();
                                                    status.setText(
                                                            "Authentication required; encrypted"
                                                                    + " data preserved");
                                                    if (automatic) {
                                                        ready.accept(null);
                                                    } else promptCredentials(target, ready);
                                                }
                                            }));
            return;
        }
        // An unencrypted key can authenticate unattended. Password/passphrase targets need user
        // entry.
        if (automatic) {
            if (target.getPrivateKeyPath() != null && !target.getPrivateKeyPath().isBlank()) {
                ready.accept(Credentials.EMPTY);
            } else {
                startupAttention = true;
                stage.show();
                status.setText(target.getName() + ": Authentication required");
                ready.accept(null);
            }
            return;
        }
        promptCredentials(target, ready);
    }

    private void promptCredentials(SshConfig target, Consumer<Credentials> ready) {
        stage.show();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Authenticate: " + target.getName());
        PasswordField password = new PasswordField(), passphrase = new PasswordField();
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                8,
                                new Label("Login password (leave blank for key authentication)"),
                                password,
                                new Label("Private-key passphrase"),
                                passphrase));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            Credentials value = new Credentials(password.getText(), passphrase.getText());
            credentials.put(target.getId(), value);
            ready.accept(value);
        } else ready.accept(null);
        password.clear();
        passphrase.clear();
    }

    private void disconnectSelected() {
        TargetCoordinator c = selectedCoordinator();
        if (c != null
                && (!c.transferring()
                        || Fx.confirm("Disconnect?", "Cancel the active transfer and disconnect?")))
            observe(c.disconnect(config.getCleanupTimeoutSeconds()));
    }

    private void observe(CompletableFuture<?> future) {
        future.exceptionally(
                error -> {
                    Platform.runLater(
                            () -> {
                                startupAttention = true;
                                stage.show();
                                Fx.error(Fx.message(error));
                            });
                    return null;
                });
    }

    private void refresh() {
        Profile p = selected();
        TargetCoordinator c = selectedCoordinator();
        boolean connected = c != null && c.connected(), busy = c != null && c.busy();
        connect.setDisable(p == null || connected || busy || recovery || migration || closing);
        disconnect.setDisable(c == null || (!connected && !busy) || closing);
        retry.setDisable(!connected || busy || closing);
        run.setDisable(!connected || busy || closing);
        cancel.setDisable(!busy || closing);
        List<NamedCommand> commands =
                p == null
                        ? List.of()
                        : c != null && connected
                                ? c.settings().getCommands()
                                : p.getTargets().getFirst().getCommands();
        String chosen = command.getValue() == null ? null : command.getValue().getId();
        command.getItems().setAll(commands);
        if (chosen != null)
            commands.stream()
                    .filter(v -> v.getId().equals(chosen))
                    .findFirst()
                    .ifPresent(command::setValue);
        if (command.getValue() == null && !commands.isEmpty())
            command.getSelectionModel().selectFirst();
        if (p == null) {
            details.setText("Create a profile to configure an SSH target.");
            return;
        }
        SshConfig t = c != null && c.connected() ? c.settings() : p.getTargets().getFirst();
        TargetCoordinator.Snapshot s = states.get(t.getId());
        StringBuilder text =
                new StringBuilder(
                        p.getName()
                                + "\n"
                                + t.getUsername()
                                + "@"
                                + t.getHostname()
                                + ":"
                                + t.getPort()
                                + "\n");
        if (s != null) {
            text.append(s.state())
                    .append(s.busy() ? " — working" : "")
                    .append("\n")
                    .append(s.message())
                    .append("\n\nForwardings:\n");
            for (PortMapping m : t.getPortMappings())
                text.append(m)
                        .append(": ")
                        .append(s.mappings().getOrDefault(m.getId(), "Not started"))
                        .append("\n");
            text.append("\nCommand results:\n");
            s.commands()
                    .forEach(
                            (id, result) ->
                                    text.append(label(t, id))
                                            .append(": ")
                                            .append(result.outcome())
                                            .append(" (exit ")
                                            .append(result.exitCode())
                                            .append(")\n")
                                            .append(result.output())
                                            .append("\n\n"));
        } else text.append("Not connected");
        details.setText(text.toString());
    }

    private String label(SshConfig target, String id) {
        if (id.startsWith("manual:"))
            return target.getCommands().stream()
                    .filter(c -> ("manual:" + c.getId()).equals(id))
                    .map(NamedCommand::getName)
                    .findFirst()
                    .orElse(id);
        String assignmentId = id.startsWith("cleanup:") ? id.substring(8) : id;
        List<CommandAssignment> assignments = new ArrayList<>(target.getAssignments());
        target.getPortMappings().forEach(m -> assignments.addAll(m.getAssignments()));
        return assignments.stream()
                .filter(a -> a.getId().equals(assignmentId))
                .findFirst()
                .map(
                        a ->
                                a.getEvent()
                                        + " / "
                                        + target.getCommands().stream()
                                                .filter(c -> c.getId().equals(a.getCommandId()))
                                                .map(NamedCommand::getName)
                                                .findFirst()
                                                .orElse("Command"))
                .orElse(id);
    }

    private void addProfile() {
        if (recovery || closing) return;
        Profile p = new Profile();
        p.getTargets().add(new SshConfig());
        edit(p, true);
    }

    private void editSelected() {
        if (selected() != null && !recovery && !closing) edit(selected(), false);
    }

    private void edit(Profile profile, boolean isNew) {
        if (editor != null) {
            editor.toFront();
            return;
        }
        SshConfig target = profile.getTargets().getFirst();
        // Editing must remain possible without unlocking saved credentials. Blank fields retain
        // ciphertext.
        Consumer<Credentials> open =
                value -> openEditor(profile, isNew, value == null ? Credentials.EMPTY : value);
        Credentials cached = credentials.get(target.getId());
        if (cached != null || target.getEncryptedCredentials() == null)
            open.accept(cached == null ? Credentials.EMPTY : cached);
        else
            CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return protection.decrypt(
                                            target.getId(), target.getEncryptedCredentials());
                                } catch (Exception e) {
                                    return null;
                                }
                            },
                            background)
                    .thenAccept(value -> Platform.runLater(() -> open.accept(value)));
    }

    private void openEditor(Profile profile, boolean isNew, Credentials initial) {
        if (editor != null || closing || saving) return;
        SshConfig target = profile.getTargets().getFirst();
        SshConfigForm form =
                forms.computeIfAbsent(
                        target.getId(),
                        id -> freeForms.isEmpty() ? SshConfigForm.create() : freeForms.pop());
        form.load(profile.getName(), target, initial);
        openForm = form;
        Stage window = new Stage();
        editor = window;
        window.initOwner(stage);
        window.initModality(Modality.WINDOW_MODAL);
        window.setTitle("Edit SSH profile");
        Button save = new Button(migration ? "Apply to migration review" : "Save"),
                discard = new Button("Cancel");
        Label note =
                new Label(
                        "Changes apply after reconnecting. Blank locked credentials retain their"
                                + " encrypted values.");
        note.setWrapText(true);
        editorDiscard =
                () -> {
                    form.clearSecrets();
                    window.hide();
                    editor = null;
                    openForm = null;
                    quitAfterSave = false;
                    editorSave = null;
                    editorDiscard = null;
                    if (isNew) {
                        forms.remove(target.getId());
                        freeForms.push(form);
                    }
                };
        editorSave =
                () -> {
                    try {
                        SshConfig value = form.value();
                        Credentials entered = form.credentials();
                        AppConfig candidate = Configurations.copy(config, AppConfig.class);
                        Profile edited = Configurations.copy(profile, Profile.class);
                        edited.setName(form.profileName());
                        edited.setTargets(new ArrayList<>(List.of(value)));
                        if (isNew) candidate.getProfiles().add(edited);
                        else candidate.getProfiles().set(index(candidate, profile.getId()), edited);
                        if (!value.isSaveCredentials()) value.setEncryptedCredentials(null);
                        if (migration) {
                            AppConfig entry = new AppConfig();
                            entry.getProfiles().add(edited);
                            Configurations.validate(entry);
                        } else Configurations.validate(candidate);
                        Runnable done =
                                () -> {
                                    if (!entered.equals(Credentials.EMPTY)
                                            || value.getEncryptedCredentials() == null)
                                        credentials.put(value.getId(), entered);
                                    config = candidate;
                                    profiles.setItems(
                                            FXCollections.observableArrayList(
                                                    config.getProfiles()));
                                    profiles.getSelectionModel().select(edited);
                                    form.markSaved();
                                    form.clearSecrets();
                                    window.hide();
                                    editor = null;
                                    openForm = null;
                                    editorSave = null;
                                    editorDiscard = null;
                                    refresh();
                                    if (quitAfterSave) {
                                        quitAfterSave = false;
                                        shutdown();
                                        return;
                                    }
                                    TargetCoordinator c = coordinators.get(value.getId());
                                    if (c != null
                                            && c.connected()
                                            && Fx.confirm(
                                                    "Reconnect?",
                                                    "Saved changes apply after reconnecting."
                                                            + " Reconnect now?"))
                                        observe(
                                                c.disconnect(config.getCleanupTimeoutSeconds())
                                                        .thenRun(
                                                                () ->
                                                                        Platform.runLater(
                                                                                () ->
                                                                                        connect(
                                                                                                value,
                                                                                                false))));
                                };
                        if (migration) {
                            done.run();
                            return;
                        }
                        boolean changed = !entered.equals(initial);
                        save.setDisable(true);
                        window.getScene().getRoot().setDisable(true);
                        saving = true;
                        CompletableFuture.runAsync(
                                        () -> {
                                            try {
                                                if (value.isSaveCredentials()
                                                        && (changed
                                                                || value.getEncryptedCredentials()
                                                                        == null)) {
                                                    SecretEnvelope envelope =
                                                            value.getEncryptedCredentials();
                                                    try {
                                                        value.setEncryptedCredentials(
                                                                protection.encrypt(
                                                                        value.getId(),
                                                                        entered,
                                                                        envelope));
                                                    } catch (Exception e) {
                                                        if (envelope != null
                                                                && Fx.confirm(
                                                                        "Replace saved"
                                                                                + " credentials?",
                                                                        "The original encryption"
                                                                            + " key could not be"
                                                                            + " used. Replace these"
                                                                            + " saved credentials"
                                                                            + " with the values you"
                                                                            + " entered?"))
                                                            value.setEncryptedCredentials(
                                                                    protection.encrypt(
                                                                            value.getId(),
                                                                            entered,
                                                                            null));
                                                        else throw e;
                                                    }
                                                }
                                                repository.save(candidate);
                                            } catch (Exception e) {
                                                throw new CompletionException(e);
                                            }
                                        },
                                        background)
                                .whenComplete(
                                        (unused, error) ->
                                                Platform.runLater(
                                                        () -> {
                                                            saving = false;
                                                            save.setDisable(false);
                                                            window.getScene()
                                                                    .getRoot()
                                                                    .setDisable(false);
                                                            if (error != null)
                                                                Fx.error(Fx.message(error));
                                                            else done.run();
                                                        }));
                    } catch (Exception e) {
                        Fx.error(Fx.message(e));
                    }
                };
        save.setOnAction(e -> editorSave.run());
        discard.setOnAction(e -> editorDiscard.run());
        VBox body = new VBox(10, form, note, new HBox(8, save, discard));
        body.setPadding(new Insets(12));
        VBox.setVgrow(form, Priority.ALWAYS);
        window.setScene(new Scene(body, 680, 630));
        window.setOnCloseRequest(
                e -> {
                    e.consume();
                    closeEditor(false);
                });
        window.show();
    }

    private int index(AppConfig document, String id) {
        for (int i = 0; i < document.getProfiles().size(); i++)
            if (document.getProfiles().get(i).getId().equals(id)) return i;
        throw new IllegalArgumentException("Profile no longer exists");
    }

    private void closeEditor(boolean quit) {
        if (saving) return;
        if (editor == null) {
            if (quit) shutdown();
            return;
        }
        if (!openForm.dirty()) {
            editorDiscard.run();
            if (quit) shutdown();
            return;
        }
        Alert dialog =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Save changes before closing?",
                        ButtonType.YES,
                        ButtonType.NO,
                        ButtonType.CANCEL);
        dialog.setHeaderText("Unsaved SSH profile");
        ButtonType answer = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == ButtonType.NO) {
            editorDiscard.run();
            if (quit) shutdown();
        } else if (answer == ButtonType.YES) {
            quitAfterSave = quit;
            editorSave.run();
        }
    }

    private void deleteSelected() {
        Profile p = selected();
        if (p == null
                || recovery
                || closing
                || !Fx.confirm(
                        migration ? "Exclude entry?" : "Delete profile?",
                        p.getName()
                                + ": "
                                + (migration
                                        ? "Explicitly exclude this legacy entry? The original file"
                                                + " is preserved."
                                        : "Delete this profile and disconnect its session?")))
            return;
        SshConfig target = p.getTargets().getFirst();
        TargetCoordinator c = coordinators.get(target.getId());
        CompletableFuture<Void> stopped =
                c == null
                        ? CompletableFuture.completedFuture(null)
                        : c.disconnect(config.getCleanupTimeoutSeconds());
        stopped.whenComplete(
                (unused, error) ->
                        Platform.runLater(
                                () -> {
                                    if (error != null) {
                                        Fx.error(Fx.message(error));
                                        return;
                                    }
                                    AppConfig candidate =
                                            Configurations.copy(config, AppConfig.class);
                                    candidate
                                            .getProfiles()
                                            .removeIf(v -> v.getId().equals(p.getId()));
                                    Runnable done =
                                            () -> {
                                                config = candidate;
                                                credentials.remove(target.getId());
                                                if (c != null) c.close();
                                                coordinators.remove(target.getId());
                                                states.remove(target.getId());
                                                SshConfigForm form = forms.remove(target.getId());
                                                if (form != null) {
                                                    form.clearSecrets();
                                                    freeForms.push(form);
                                                }
                                                profiles.setItems(
                                                        FXCollections.observableArrayList(
                                                                config.getProfiles()));
                                                refresh();
                                            };
                                    if (migration) done.run();
                                    else saveConfiguration(candidate, done);
                                }));
    }

    private void applicationSettings() {
        if (recovery || closing) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Application settings");
        CheckBox trayOnly = new CheckBox("Start in system tray only");
        trayOnly.setSelected(config.isStartInTray());
        TextField timeout = new TextField("" + config.getCleanupTimeoutSeconds());
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                8,
                                trayOnly,
                                new Label("Overall cleanup timeout seconds"),
                                timeout));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
            try {
                AppConfig candidate = Configurations.copy(config, AppConfig.class);
                candidate.setStartInTray(trayOnly.isSelected());
                candidate.setCleanupTimeoutSeconds(Integer.parseInt(timeout.getText()));
                Configurations.validate(candidate);
                if (migration) config = candidate;
                else saveConfiguration(candidate, () -> config = candidate);
            } catch (Exception e) {
                Fx.error(Fx.message(e));
            }
    }

    private void saveConfiguration(AppConfig candidate, Runnable done) {
        if (saving || recovery) return;
        saving = true;
        stage.getScene().getRoot().setDisable(true);
        CompletableFuture.runAsync(
                        () -> {
                            try {
                                // Migration credentials are kept outside the document until the
                                // user opts into saving.
                                if (migration)
                                    for (Profile p : candidate.getProfiles())
                                        for (SshConfig t : p.getTargets())
                                            if (t.isSaveCredentials())
                                                t.setEncryptedCredentials(
                                                        protection.encrypt(
                                                                t.getId(),
                                                                credentials.getOrDefault(
                                                                        t.getId(),
                                                                        Credentials.EMPTY),
                                                                t.getEncryptedCredentials()));
                                repository.save(candidate);
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        },
                        background)
                .whenComplete(
                        (unused, error) ->
                                Platform.runLater(
                                        () -> {
                                            saving = false;
                                            stage.getScene().getRoot().setDisable(false);
                                            if (error != null) Fx.error(Fx.message(error));
                                            else {
                                                done.run();
                                                refresh();
                                            }
                                        }));
    }

    private void transfer(boolean upload) {
        TargetCoordinator c = selectedCoordinator();
        if (c == null || !c.connected()) {
            Fx.error("Connect this target first");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(upload ? "Select file to upload" : "Save downloaded file");
        java.io.File file = upload ? chooser.showOpenDialog(stage) : chooser.showSaveDialog(stage);
        if (file == null) return;
        TextInputDialog remote =
                new TextInputDialog(
                        upload
                                ? c.settings().getUploadDirectory()
                                        + (c.settings().getUploadDirectory().endsWith("/")
                                                ? ""
                                                : "/")
                                        + file.getName()
                                : c.settings().getUploadDirectory());
        remote.setHeaderText("Remote file path");
        Optional<String> path = remote.showAndWait();
        if (path.isEmpty() || path.get().isBlank()) return;
        Stage progressWindow = new Stage();
        progressWindow.initOwner(stage);
        progressWindow.setTitle(upload ? "Uploading" : "Downloading");
        ProgressBar progress = new ProgressBar(0);
        progress.setPrefWidth(360);
        Label result = new Label("Transferring…");
        Button cancelTransfer = new Button("Cancel transfer");
        cancelTransfer.setOnAction(e -> c.cancelTransfer());
        VBox body = new VBox(12, progress, result, cancelTransfer);
        body.setPadding(new Insets(15));
        progressWindow.setScene(new Scene(body));
        progressWindow.setOnCloseRequest(
                e -> {
                    e.consume();
                    c.cancelTransfer();
                });
        progressWindow.show();
        c.transfer(
                        upload,
                        file.toPath(),
                        path.get(),
                        value -> Platform.runLater(() -> progress.setProgress(value)),
                        () ->
                                Fx.confirm(
                                        "Overwrite file?",
                                        "Destination already exists. Replace it?"))
                .whenComplete(
                        (unused, error) ->
                                Platform.runLater(
                                        () -> {
                                            result.setText(
                                                    error == null
                                                            ? "Transfer complete"
                                                            : Fx.message(error));
                                            cancelTransfer.setText("Close");
                                            cancelTransfer.setOnAction(e -> progressWindow.close());
                                            progressWindow.setOnCloseRequest(null);
                                        }));
    }

    private void terminal() {
        Profile p = selected();
        if (p == null) return;
        TargetCoordinator c = selectedCoordinator();
        SshConfig target = c != null && c.connected() ? c.settings() : p.getTargets().getFirst();
        observe(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                TerminalLauncher.launch(target);
                            } catch (Exception e) {
                                throw new CompletionException(e);
                            }
                        },
                        background));
    }

    private void requestQuit() {
        if (closing) return;
        if (migration) {
            if (!Fx.confirm(
                    "Discard migration review?",
                    "Quit without accepting this migration review? The original configuration file"
                            + " is unchanged.")) return;
            if (editor != null) editorDiscard.run();
            shutdown();
            return;
        }
        if (editor != null) {
            closeEditor(true);
            return;
        }
        if (saving) {
            status.setText("Waiting for configuration save before Quit");
            return;
        }
        shutdown();
    }

    private void shutdown() {
        closing = true;
        stage.show();
        stage.getScene().getRoot().setDisable(true);
        status.setText("Disconnecting and cleaning up…");
        if (tray != null) tray.close();
        CompletableFuture<?>[] tasks =
                coordinators.values().stream()
                        .map(c -> c.disconnect(config.getCleanupTimeoutSeconds()))
                        .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks)
                .orTimeout(config.getCleanupTimeoutSeconds() + 2L, TimeUnit.SECONDS)
                .whenComplete(
                        (unused, error) ->
                                Platform.runLater(
                                        () -> {
                                            coordinators.values().forEach(TargetCoordinator::close);
                                            Platform.exit();
                                        }));
    }

    @Override
    public void stop() {
        if (tray != null) tray.close();
        coordinators.values().forEach(TargetCoordinator::close);
        credentials.clear();
        forms.values().forEach(SshConfigForm::clearSecrets);
        poller.shutdownNow();
        background.shutdownNow();
    }
}
