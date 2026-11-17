# SSH Auto Connect

JavaFX desktop manager for independent SSH profiles, local forwarding, named remote
commands, single-file SFTP transfers and external terminals. NeitzelLib supplies
FXML Component bindings, native configuration paths and Open/Quit tray lifecycle.

Use Java 25 and the included Maven 3.9.11 wrapper:

```sh
./mvnw verify
./mvnw javafx:run
```

Desktop tests need a display; on Linux use `xvfb-run ./mvnw verify`. The optional
native credential-store test creates and removes a synthetic keychain entry:

```sh
./mvnw verify -Dssh.native.credentials=true
```

Ordinary verification reports that one test as skipped. The native test requires
an accessible OS credential store. Surefire failures fail the build. SSH/SFTP
integration tests use temporary files and a loopback Apache SSHD server; they do
not use your SSH targets or credentials.

## Configuration and first run

Configuration is a versioned `config.json` in the native application directory:

| Platform | Directory |
| --- | --- |
| macOS | `~/Library/Application Support/SSHAutoConnect` |
| Windows | `%APPDATA%/SSHAutoConnect`, with the user's roaming-directory fallback |
| Linux | `$XDG_CONFIG_HOME/SSHAutoConnect` or `~/.config/SSHAutoConnect` |

Create a profile, edit its target, then Connect. Profiles and targets have stable
IDs; renames preserve identity. The format already contains a target collection,
while this release exposes exactly one target per profile. Multiple profiles can
connect concurrently. Forwardings bind to local loopback and connect to the remote
server's localhost. Auto-connect means startup connection, with no automatic retry.

If `~/.sshAutoConnect-configs.json` exists and no new configuration exists, review
its entries before accepting migration. Correct or explicitly exclude each invalid
entry. Existing passwords default to session-only use; encrypted saving is opt-in.
Imported command names are `Start 1:`, `Stop 1:`, etc., using mapping order. Start
commands retain their before-forwarding assignment; Stop commands initially have
no event assignments because the old application never executed them.

Migration never modifies the old file, which still contains its original plaintext
passwords. Quit can discard an unaccepted review. An existing new-format file wins;
invalid/unsupported configuration opens recovery mode without connections or
saving over it. Correct that file externally and restart. Saves validate and
atomically replace the document; failures preserve the previous document.

## Credentials and server identity

Unknown server keys require fingerprint confirmation before writing to
`~/.ssh/known_hosts`. Changed keys are blocked; verify and correct them deliberately
outside the ordinary Connect flow. External terminals use their own SSH client.

Password and private-key-passphrase persistence is optional per target. Saved
credentials use AES-256-GCM with target identity as authenticated data and a fresh
nonce. Encryption keys are held in the OS credential store through java-keyring;
there is no file-based key fallback. Otherwise credentials remain in application
memory. An unavailable store leaves ciphertext intact and permits session-only
entry. Moving configuration to another machine requires re-entering credentials.
Replacing credentials whose original key is missing requires confirmation.

OS credential-store protection follows the platform's user/session and application
identity rules. The developer app image is unsigned; signing/notarization and
production distribution identity are not configured by these scripts.

## Commands, partial connections and editing

Commands have names, remote shell text and a timeout (30 seconds by default).
Every command can run manually over an existing SSH session. Event assignments are
optional, ordered, and choose Required or Best effort (default):

- Per target: after SSH connected, before SSH disconnected.
- Per mapping: before/after starting forwarding, before/after stopping forwarding.

A required preparation failure skips its guarded setup. A required after-forwarding
failure retains the tunnel but marks setup incomplete. Unrelated mappings remain
usable. The UI distinguishes Not connected, Partly connected and Connected, with
per-mapping results and bounded command output. Best-effort failures warn without
changing an otherwise complete connection's status.

Retry failed setup resumes incomplete required steps and failed forwarding without
repeating successful work. Full reconnect reruns the configured lifecycle. Manual
command execution does not repair tracked setup status. Cancel, timeout or lost
SSH leave the remote outcome unconfirmed; closing a channel cannot prove the
remote process stopped. No command is retried automatically.

Cleanup events run for mappings whose startup was attempted, even when forwarding
failed. Cleanup errors do not veto disconnect. The overall cleanup deadline defaults
to 60 seconds; remaining commands are skipped at expiry and local resources close.
Failed cleanup appears in results and logs. Commands must terminate without
interactive input; start services in the background.

Editors use drafts. Cancel discards edits; Save affects the next connection and
offers reconnect. Active sessions retain their command/settings snapshot, including
for manual Run and cleanup. The Component's presentation model stays fixed; editor
instances are reused to bound NeitzelLib's model-cache retention by peak editor
count. Runtime state and plaintext credentials are not part of that draft model.

Transfers have progress, cancellation and overwrite confirmation, one at a time
per target. Downloads use a temporary file to preserve an existing destination on
failure. Interrupted uploads may leave partial remote files. External terminals
remain independent after the application's session disconnects.

## Tray and distribution

Application settings include Start in system tray (off by default) and the cleanup
deadline. Window close hides only after successful tray installation. Unavailable
tray or required startup interaction keeps the application accessible. Open/Quit
are available in the tray, and Quit is also in the JavaFX window. Native macOS Quit
uses the same unsaved-edit and cleanup path.

Build the full application and its native installer on the target platform:

```sh
./mvnw -Pjpackage verify
# Equivalent convenience entry point:
./scripts/package-app
```

The application contains its own Java runtime and dependencies; end users do not
need to install Java. Maven stages only runtime dependencies and the current JAR
in `target/jpackage/input`, builds the app image, then packages that same image
into an installer. Native application versions are derived from the Maven version
(`1.1-SNAPSHOT` becomes `1.1.0`); override with `-Dapp.version=1.2.3` when needed.

| Build platform | Launchable application | Native installer |
| --- | --- | --- |
| macOS | `target/dist/image/SSHAutoConnect.app` | `target/dist/installer/SSHAutoConnect-1.1.0.dmg` |
| Windows | `target/dist/image/SSHAutoConnect/SSHAutoConnect.exe` | MSI under `target/dist/installer` |
| Linux | `target/dist/image/SSHAutoConnect/bin/SSHAutoConnect` | DEB under `target/dist/installer` |

On macOS, open the DMG and drag SSHAutoConnect into Applications, or launch the
`.app` directly. Native installers must be built on their target OS. Windows MSI
builds need the WiX tools required by the selected JDK; Linux DEB builds need
`dpkg-deb` and `fakeroot`. To build only a portable app image without installer
prerequisites:

```sh
./mvnw -Pjpackage -Djpackage.installer.skip=true verify
# Or:
./scripts/package-app --image-only
```

For incremental builds after a successful verification, `--skip-tests` skips
running tests while Maven still compiles/stages the current application. The old
`--skip-build` flag is retained as an alias. On Windows use
`./scripts/package-app.ps1`, optionally with `-SkipTests` (alias `-SkipBuild`) and
`-ImageOnly`. Additional Maven options are forwarded by both wrappers.

Rebuilding refreshes the packaging input and dedicated `target/dist/image` and
`target/dist/installer` output directories, so repeated builds work without
`clean`. The app has an explicit macOS bundle identifier
`de.kneitzel.SSHAutoConnect`, platform icons using the existing artwork, version
metadata and a bundled license. Windows installers have a stable upgrade UUID,
per-user installation and Start Menu/desktop shortcuts. These local artifacts
are not Developer ID signed or notarized.

The shaded executable JAR and Graal native profiles have been retired. Linux
external terminals use the configured executable (default `xterm`); Windows uses
`wt.exe`; macOS uses Terminal.

Implementation evidence and remaining platform qualification are recorded in the
workspace's SSHAutoConnect efforts. Windows/Linux native packaging, tray, terminal
and key-store behavior require validation on those platforms.
