package de.kneitzel.ui;

import de.kneitzel.*;
import de.kneitzel.config.Configurations;
import de.kneitzel.model.*;
import de.kneitzel.security.Credentials;
import de.neitzel.fx.view.*;
import de.neitzel.injection.ApplicationContext;

import jakarta.inject.Singleton;

import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.Consumer;

/** A real NeitzelLib FXML Component. Its presentation model is assigned exactly once. */
@Singleton
public class SshConfigForm extends VBox implements HasViewModel {
    private final ObjectProperty<Object> model = new SimpleObjectProperty<>(this, "model");
    private ViewModel viewModel;
    private SshConfig working;
    private String baseline;
    @FXML private PasswordField password, passphrase;
    @FXML private ListView<PortMapping> mappings;
    @FXML private ListView<NamedCommand> commands;
    @FXML private ListView<CommandAssignment> sessionEvents;

    public ObjectProperty<Object> modelProperty() {
        return model;
    }

    public Object getModel() {
        return model.get();
    }

    public void setModel(Object value) {
        if (model.get() != null && model.get() != value)
            throw new IllegalStateException("Component model cannot be replaced");
        model.set(value);
    }

    @Override
    public void setViewModel(ViewModel value) {
        viewModel = value;
    }

    public static SshConfigForm create() {
        TargetDraft draft = new TargetDraft();
        var loaded =
                ViewLoader.load(
                        SshConfigForm.class.getResource("EditorShell.fxml"),
                        draft,
                        new ApplicationContext("de.kneitzel.ui"));
        VBox shell = (VBox) loaded.getRoot();
        SshConfigForm form = (SshConfigForm) shell.getChildren().getFirst();
        shell.getChildren().clear();
        form.setModel(draft);
        return form;
    }

    public void load(String profileName, SshConfig config, Credentials credentials) {
        working = Configurations.copy(config, SshConfig.class);
        set("profileName", profileName);
        set("name", config.getName());
        set("hostname", config.getHostname());
        set("port", Integer.toString(config.getPort()));
        set("username", config.getUsername());
        set("privateKeyPath", config.getPrivateKeyPath());
        set("xtermCommand", config.getXtermCommand());
        set("uploadDirectory", config.getUploadDirectory());
        set("autoConnect", config.isAutoConnect());
        set("saveCredentials", config.isSaveCredentials());
        password.setText(credentials.password());
        passphrase.setText(credentials.passphrase());
        refresh();
        baseline = signature();
    }

    @SuppressWarnings("unchecked")
    private void set(String name, Object value) {
        ((javafx.beans.property.Property<Object>) (Object) viewModel.property(name))
                .setValue(value);
    }

    public String profileName() {
        return ((TargetDraft) getModel()).getProfileName().strip();
    }

    public SshConfig value() {
        TargetDraft d = (TargetDraft) getModel();
        SshConfig result = Configurations.copy(working, SshConfig.class);
        result.setName(d.getName());
        result.setHostname(d.getHostname().strip());
        result.setPort(Integer.parseInt(d.getPort().strip()));
        result.setUsername(d.getUsername().strip());
        result.setPrivateKeyPath(d.getPrivateKeyPath());
        result.setXtermCommand(d.getXtermCommand());
        result.setUploadDirectory(d.getUploadDirectory());
        result.setAutoConnect(d.isAutoConnect());
        result.setSaveCredentials(d.isSaveCredentials());
        return result;
    }

    public Credentials credentials() {
        return new Credentials(password.getText(), passphrase.getText());
    }

    public boolean dirty() {
        return !Objects.equals(baseline, signature());
    }

    public void markSaved() {
        baseline = signature();
    }

    public void clearSecrets() {
        password.clear();
        passphrase.clear();
        baseline = null;
    }

    private String signature() {
        return new com.google.gson.Gson().toJson(getModel())
                + new com.google.gson.Gson().toJson(working)
                + "|"
                + password.getText()
                + "|"
                + passphrase.getText();
    }

    private void refresh() {
        mappings.getItems().setAll(working.getPortMappings());
        commands.getItems().setAll(working.getCommands());
        sessionEvents.getItems().setAll(working.getAssignments());
        renderAssignments(sessionEvents);
    }

    @FXML
    private void addMapping() {
        mappingDialog(
                new PortMapping(),
                m -> {
                    working.getPortMappings().add(m);
                    refresh();
                });
    }

    @FXML
    private void editMapping() {
        PortMapping m = mappings.getSelectionModel().getSelectedItem();
        if (m != null)
            mappingDialog(
                    Configurations.copy(m, PortMapping.class),
                    value -> {
                        int i = working.getPortMappings().indexOf(m);
                        working.getPortMappings().set(i, value);
                        refresh();
                    });
    }

    @FXML
    private void removeMapping() {
        PortMapping m = mappings.getSelectionModel().getSelectedItem();
        if (m != null) {
            working.getPortMappings().remove(m);
            refresh();
        }
    }

    private void mappingDialog(PortMapping mapping, Consumer<PortMapping> save) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Forwarding");
        TextField local = new TextField("" + mapping.getLocalPort()),
                remote = new TextField("" + mapping.getRemotePort());
        ListView<CommandAssignment> events = new ListView<>();
        events.getItems().setAll(mapping.getAssignments());
        events.setPrefHeight(160);
        renderAssignments(events);
        Button add = new Button("Assign event"), remove = new Button("Remove assignment");
        add.setOnAction(
                e ->
                        assignment(
                                false,
                                a -> {
                                    mapping.getAssignments().add(a);
                                    events.getItems().setAll(mapping.getAssignments());
                                }));
        remove.setOnAction(
                e -> {
                    mapping.getAssignments().remove(events.getSelectionModel().getSelectedItem());
                    events.getItems().setAll(mapping.getAssignments());
                });
        Button up = new Button("Move up"), down = new Button("Move down");
        up.setOnAction(e -> move(mapping.getAssignments(), events, -1));
        down.setOnAction(e -> move(mapping.getAssignments(), events, 1));
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                8,
                                new Label("Local port (loopback only)"),
                                local,
                                new Label("Remote localhost port"),
                                remote,
                                new Label("Events — execution order"),
                                events,
                                new HBox(6, add, remove, up, down)));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane()
                .lookupButton(ButtonType.OK)
                .addEventFilter(
                        javafx.event.ActionEvent.ACTION,
                        e -> {
                            try {
                                int l = Integer.parseInt(local.getText()),
                                        r = Integer.parseInt(remote.getText());
                                if (l < 1 || l > 65535 || r < 1 || r > 65535)
                                    throw new IllegalArgumentException();
                                mapping.setLocalPort(l);
                                mapping.setRemotePort(r);
                            } catch (Exception x) {
                                e.consume();
                                Fx.error("Ports must be 1–65535");
                            }
                        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) save.accept(mapping);
    }

    @FXML
    private void addCommand() {
        commandDialog(
                new NamedCommand(),
                c -> {
                    working.getCommands().add(c);
                    refresh();
                });
    }

    @FXML
    private void editCommand() {
        NamedCommand c = commands.getSelectionModel().getSelectedItem();
        if (c != null)
            commandDialog(
                    Configurations.copy(c, NamedCommand.class),
                    value -> {
                        working.getCommands().set(working.getCommands().indexOf(c), value);
                        refresh();
                    });
    }

    @FXML
    private void removeCommand() {
        NamedCommand c = commands.getSelectionModel().getSelectedItem();
        if (c == null) return;
        boolean used =
                working.getAssignments().stream().anyMatch(a -> a.getCommandId().equals(c.getId()))
                        || working.getPortMappings().stream()
                                .flatMap(m -> m.getAssignments().stream())
                                .anyMatch(a -> a.getCommandId().equals(c.getId()));
        if (used) {
            Fx.error("Remove this command's event assignments first");
            return;
        }
        working.getCommands().remove(c);
        refresh();
    }

    private void commandDialog(NamedCommand command, Consumer<NamedCommand> save) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Named remote command");
        TextField name = new TextField(command.getName()),
                timeout = new TextField("" + command.getTimeoutSeconds());
        TextArea text = new TextArea(command.getCommand());
        text.setPrefRowCount(6);
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                8,
                                new Label("Name"),
                                name,
                                new Label("Remote shell command (non-interactive)"),
                                text,
                                new Label("Timeout seconds"),
                                timeout));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane()
                .lookupButton(ButtonType.OK)
                .addEventFilter(
                        javafx.event.ActionEvent.ACTION,
                        e -> {
                            try {
                                int seconds = Integer.parseInt(timeout.getText());
                                if (name.getText().isBlank()
                                        || text.getText().isBlank()
                                        || seconds < 1
                                        || seconds > 3600) throw new IllegalArgumentException();
                                command.setName(name.getText().strip());
                                command.setCommand(text.getText());
                                command.setTimeoutSeconds(seconds);
                            } catch (Exception x) {
                                e.consume();
                                Fx.error("Provide a name, command and timeout of 1–3600 seconds");
                            }
                        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) save.accept(command);
    }

    private void assignment(boolean session, Consumer<CommandAssignment> save) {
        if (working.getCommands().isEmpty()) {
            Fx.error("Create a named command first");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Optional event assignment");
        ComboBox<NamedCommand> command = new ComboBox<>();
        command.getItems().setAll(working.getCommands());
        command.getSelectionModel().selectFirst();
        ComboBox<CommandEvent> event = new ComboBox<>();
        event.getItems()
                .setAll(
                        Arrays.stream(CommandEvent.values())
                                .filter(v -> v.sessionEvent() == session)
                                .toList());
        event.getSelectionModel().selectFirst();
        CheckBox required = new CheckBox("Required (cleanup still cannot veto disconnect)");
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                8,
                                new Label("Command"),
                                command,
                                new Label("Event"),
                                event,
                                required));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            CommandAssignment a =
                    new CommandAssignment(command.getValue().getId(), event.getValue());
            a.setRequired(required.isSelected());
            save.accept(a);
        }
    }

    @FXML
    private void addSessionEvent() {
        assignment(
                true,
                a -> {
                    working.getAssignments().add(a);
                    refresh();
                });
    }

    @FXML
    private void removeSessionEvent() {
        working.getAssignments().remove(sessionEvents.getSelectionModel().getSelectedItem());
        refresh();
    }

    @FXML
    private void sessionUp() {
        move(working.getAssignments(), sessionEvents, -1);
    }

    @FXML
    private void sessionDown() {
        move(working.getAssignments(), sessionEvents, 1);
    }

    private <T> void move(List<T> values, ListView<T> view, int direction) {
        int i = view.getSelectionModel().getSelectedIndex();
        int j = i + direction;
        if (i >= 0 && j >= 0 && j < values.size()) {
            Collections.swap(values, i, j);
            view.getItems().setAll(values);
            view.getSelectionModel().select(j);
        }
    }

    private void renderAssignments(ListView<CommandAssignment> list) {
        list.setCellFactory(
                v ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(CommandAssignment a, boolean empty) {
                                super.updateItem(a, empty);
                                setText(
                                        empty || a == null
                                                ? null
                                                : a.getEvent()
                                                        + ": "
                                                        + working.getCommands().stream()
                                                                .filter(
                                                                        c ->
                                                                                c.getId()
                                                                                        .equals(
                                                                                                a
                                                                                                        .getCommandId()))
                                                                .map(NamedCommand::getName)
                                                                .findFirst()
                                                                .orElse("Missing command")
                                                        + (a.isRequired()
                                                                ? " [required]"
                                                                : " [best effort]"));
                            }
                        });
    }
}
