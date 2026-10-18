package com.pos.setup;

import com.pos.database.DatabaseConnection;
import com.pos.setup.FirstRunSetup.SetupException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Runs only Windows installation/service work with UAC elevation. */
final class WindowsMySqlProvisioner implements FirstRunSetup.Operations {
    static final String INSTALLER_NAME = "mysql-installer-web-community-8.0.46.0.msi";
    private static Path recoveryFile() { return Path.of(DatabaseConnection.CONFIG_PATH + ".setup"); }
    private final int port;

    WindowsMySqlProvisioner() { this(3306); }

    /** Isolated database port for native integration checks; production uses 3306. */
    WindowsMySqlProvisioner(int port) { this.port = port; }

    @Override public boolean serverExists() throws SetupException {
        requireWindows();
        if (responding()) return true;
        String result = powershell("$ErrorActionPreference='Stop'; "
                + "$s=@(Get-CimInstance Win32_Service | Where-Object { $_.PathName -match "
                + "'(?i)[\\\\]mysqld(?:-nt)?\\.exe(?:[\"\\s]|$)' }); "
                + "if ($s.Count -gt 0) { 'present' } else { 'missing' }", Duration.ofSeconds(30));
        return result.trim().equals("present");
    }

    @Override public void startExisting(Consumer<String> progress) throws SetupException {
        if (responding()) return;
        elevated("Start", null, progress);
        waitUntilResponding();
    }

    @Override public void install(String bootstrapPassword, Consumer<String> progress) throws SetupException {
        elevated("Install", bootstrapPassword, progress);
        waitUntilResponding();
    }

    private Connection connect(String password) throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + port + "/"
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000",
                "root", password);
    }

    @Override public boolean accepts(String password) throws SetupException {
        try (Connection ignored = connect(password)) { return true; }
        catch (SQLException ex) {
            if (authenticationFailure(ex) || connectionFailure(ex)) return false;
            throw new SetupException("The database connection could not be checked (error " + ex.getErrorCode() + ").");
        }
    }

    private boolean responding() throws SetupException {
        try (Connection ignored = connect("")) { return true; }
        catch (SQLException ex) {
            if (authenticationFailure(ex)) return true;
            if (connectionFailure(ex)) return false;
            throw new SetupException("The local database server returned error " + ex.getErrorCode() + ".");
        }
    }

    private static boolean authenticationFailure(SQLException ex) {
        return "28000".equals(ex.getSQLState()) || ex.getErrorCode() == 1045;
    }

    private static boolean connectionFailure(SQLException ex) {
        return ex.getSQLState() != null && ex.getSQLState().startsWith("08");
    }

    private void waitUntilResponding() throws SetupException {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (responding()) return;
            try { Thread.sleep(1500); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SetupException("Database setup was interrupted. Select Retry.");
            }
        }
        throw new SetupException("The database service did not become available on port 3306. "
                + "Select Retry after checking that the service can start.");
    }

    @Override public void changePassword(String current, String password) throws SetupException {
        try (Connection c = connect(current);
             PreparedStatement statement = c.prepareStatement("ALTER USER CURRENT_USER() IDENTIFIED BY ?")) {
            statement.setString(1, password);
            statement.executeUpdate();
        } catch (SQLException ex) {
            // Driver messages/SQL can contain the password; display only the error code.
            throw new SetupException("The server could not set your password (error " + ex.getErrorCode()
                    + "). Try a password with upper/lowercase letters, a number and a symbol.");
        }
    }

    @Override public void save(String password) throws SetupException {
        try {
            Properties p = new Properties();
            p.setProperty("db.host", "127.0.0.1");
            p.setProperty("db.port", "3306");
            p.setProperty("db.name", FirstRunSetup.databaseName());
            p.setProperty("db.username", "root");
            p.setProperty("db.password", password);
            p.setProperty("db.timezone", DatabaseConnection.TIMEZONE);
            storePrivately(Path.of(DatabaseConnection.CONFIG_PATH), p);
            DatabaseConnection.loadConfig();
        } catch (Exception ex) {
            throw new SetupException("The server is ready, but its connection settings could not be saved. "
                    + "Select Retry and enter the server password you chose.", ex);
        }
    }

    @Override public String recoveryPassword() throws SetupException {
        Path recoveryFile = recoveryFile();
        if (!Files.isRegularFile(recoveryFile)) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(recoveryFile)) {
            p.load(in);
            return p.getProperty("password");
        } catch (Exception ex) { throw new SetupException("Could not read the previous setup state.", ex); }
    }

    @Override public void rememberRecoveryPassword(String password) throws SetupException {
        Properties p = new Properties();
        p.setProperty("password", password);
        try {
            storePrivately(recoveryFile(), p);
        } catch (Exception ex) { throw new SetupException("Could not save database setup recovery information.", ex); }
    }

    private static void storePrivately(Path destination, Properties properties) throws Exception {
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), "mysql-config-", ".tmp");
        try {
            restrictAccess(temp, false);
            try (OutputStream out = Files.newOutputStream(temp)) { properties.store(out, "POS database configuration"); }
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    @Override public void clearRecoveryPassword() throws SetupException {
        try { Files.deleteIfExists(recoveryFile()); }
        catch (Exception ex) { throw new SetupException("Setup completed, but its temporary credentials could not be removed.", ex); }
    }

    private void elevated(String action, String bootstrapPassword, Consumer<String> progress) throws SetupException {
        Path work = null;
        try {
            work = Files.createTempDirectory("mbuyedzedzo-mysql-");
            restrictAccess(work, true);
            Path script = work.resolve("provision-mysql.ps1");
            try (InputStream in = getClass().getResourceAsStream("/setup/provision-mysql.ps1")) {
                if (in == null) throw new SetupException("The database setup helper is missing from this build.");
                Files.copy(in, script);
            }
            if (bootstrapPassword != null) Files.writeString(work.resolve("bootstrap.txt"), bootstrapPassword, StandardCharsets.US_ASCII);
            Path bundle = bundledInstaller();
            String args = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"" + script
                    + "\" -Action " + action + " -WorkDir \"" + work + "\""
                    + (bundle == null ? "" : " -BundledInstaller \"" + bundle + "\"");
            progress.accept(action.equals("Install")
                    ? "Approve the Windows administrator prompt. MySQL will install automatically; internet access is required."
                    : "Starting your database service. Approve the Windows administrator prompt if requested.");
            String command = "$ErrorActionPreference='Stop'; try { "
                    + "$p=Start-Process -FilePath ($PSHOME+'\\powershell.exe') -Verb RunAs -WindowStyle Hidden "
                    + "-ArgumentList " + psQuote(args) + " -PassThru; $null=$p.Handle; $p.WaitForExit(); exit $p.ExitCode "
                    + "} catch { Write-Output 'Administrator approval was cancelled or unavailable.'; exit 1 }";
            try { powershell(command, Duration.ofMinutes(40), work.resolve("status.txt"), progress); }
            catch (SetupException ex) {
                Path errorFile = work.resolve("error.txt");
                String detail = Files.exists(errorFile) ? Files.readString(errorFile).trim() : ex.getMessage();
                throw new SetupException(detail + "\n\nSetup log: " + work.resolve("setup.log") + "\nSelect Retry to try again.");
            }
        } catch (SetupException ex) { throw ex; }
        catch (Exception ex) { throw new SetupException("Could not prepare database installation. Select Retry.", ex); }
        finally {
            // Keep diagnostic logs; remove the temporary secret (recovery is stored separately).
            if (work != null) try { Files.deleteIfExists(work.resolve("bootstrap.txt")); } catch (Exception ignored) {}
        }
    }

    static Path bundledInstaller() {
        List<Path> roots = new ArrayList<>();
        ProcessHandle.current().info().command().ifPresent(c -> roots.add(Path.of(c).getParent()));
        try {
            Path code = Path.of(WindowsMySqlProvisioner.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            roots.add(code.getParent());
            roots.add(code.getParent().getParent());
        } catch (Exception ignored) {}
        roots.add(Path.of("").toAbsolutePath());
        for (Path root : roots) {
            for (String folder : List.of("mysql-setup", "app-content", "installer", ".")) {
                Path candidate = root.resolve(folder).resolve(INSTALLER_NAME).normalize();
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }
        return null; // Download the pinned, signed installer automatically when not bundled.
    }

    private static void restrictAccess(Path path, boolean directory) throws SetupException {
        String inheritance = directory ? "(OI)(CI)" : "";
        powershell("$ErrorActionPreference='Stop'; $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value; "
                + "& icacls.exe " + psQuote(path.toString()) + " /inheritance:r /grant:r "
                + "(('*'+$sid)+':" + inheritance + "F') '*S-1-5-18:" + inheritance
                + "F' '*S-1-5-32-544:" + inheritance + "F' | Out-Null; exit $LASTEXITCODE", Duration.ofSeconds(30));
    }

    static String psQuote(String value) { return "'" + value.replace("'", "''") + "'"; }

    private static String powershell(String script, Duration timeout) throws SetupException {
        return powershell(script, timeout, null, null);
    }

    private static String powershell(String script, Duration timeout, Path statusFile,
                                     Consumer<String> progress) throws SetupException {
        Path output = null;
        try {
            output = Files.createTempFile("pos-setup-process-", ".log");
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            long deadline = System.nanoTime() + timeout.toNanos();
            String previous = "";
            while (!p.waitFor(1, TimeUnit.SECONDS)) {
                if (System.nanoTime() >= deadline) {
                    p.destroy();
                    throw new SetupException("Database setup timed out. Allow any running Windows installer to finish before retrying.");
                }
                if (statusFile != null && Files.isRegularFile(statusFile)) {
                    try {
                        String current = Files.readString(statusFile, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
                        if (!current.isBlank() && !current.equals(previous)) { progress.accept(current); previous = current; }
                    } catch (java.io.IOException ignored) { /* The helper may be replacing the status file. */ }
                }
            }
            String result = Files.readString(output, StandardCharsets.UTF_8);
            if (p.exitValue() != 0) throw new SetupException(result.isBlank()
                    ? "Windows could not complete database setup (code " + p.exitValue() + ")." : result.trim());
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SetupException("Database setup was interrupted.");
        } catch (SetupException ex) { throw ex; }
        catch (Exception ex) { throw new SetupException("Could not run Windows database setup.", ex); }
        finally { if (output != null) try { Files.deleteIfExists(output); } catch (Exception ignored) {} }
    }

    private static void requireWindows() throws SetupException {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            throw new SetupException("Automatic database installation requires Windows.");
        }
    }
}
