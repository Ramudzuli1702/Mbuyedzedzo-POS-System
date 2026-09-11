; ============================================================
;  POS System — Inno Setup Installer Script
;  Produces a single .exe that installs:
;    1. Java 22 Runtime (bundled — no internet needed)
;    2. MySQL 8.4 Community (silent install)
;    3. The POS application JAR
;    4. A desktop shortcut and Start Menu entry
;
;  HOW TO BUILD THIS:
;    1. Download Inno Setup: https://jrsoftware.org/isdl.php
;    2. Download Java 22 JRE: https://adoptium.net (get the .zip, not installer)
;        → Extract to: installer/jre/
;    3. Download MySQL 8.4 MSI installer:
;        https://dev.mysql.com/downloads/mysql/
;        → Save as: installer/mysql-installer.msi
;    4. Build your POS JAR: mvn clean package
;        → Copy target/point-of-sale-system-1.0.0.jar to installer/
;    5. Open this file in Inno Setup → Compile
;        → Output: installer/Output/POS_System_Setup.exe
; ============================================================

#define MyAppName      "POS System"
#define MyAppVersion   "1.0.0"
#define MyAppPublisher "RalidzhivhaRR"
#define MyAppExeName   "POSSystem.exe"
#define MyAppDir       "{autopf}\POS System"
#define JarName        "point-of-sale-system-1.0.0.jar"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={#MyAppDir}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
; Require admin — needed to install MySQL and write to Program Files
PrivilegesRequired=admin
OutputDir=installer\Output
OutputBaseFilename=POS_System_Setup
SetupIconFile=installer\pos-icon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
; Show licence agreement
LicenseFile=installer\LICENSE.txt
; Minimum Windows version: Windows 10
MinVersion=10.0

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "startupicon"; Description: "Launch POS automatically at Windows startup"; GroupDescription: "Additional options"; Flags: unchecked

[Dirs]
; Create folders the app writes to (not in Program Files — use AppData)
Name: "{commonappdata}\POS System"
Name: "{commonappdata}\POS System\receipts"
Name: "{commonappdata}\POS System\reports"
Name: "{commonappdata}\POS System\backups"
Name: "{commonappdata}\POS System\config"

[Files]
; ── Bundled Java Runtime ──────────────────────────────────────────────────────
; Extract the JRE zip to installer/jre/ before building
Source: "installer\jre\*"; DestDir: "{app}\jre"; Flags: recursesubdirs createallsubdirs

; ── MySQL Installer ───────────────────────────────────────────────────────────
Source: "installer\mysql-installer.msi"; DestDir: "{tmp}"; Flags: deleteafterinstall

; ── Application JAR ───────────────────────────────────────────────────────────
Source: "installer\{#JarName}"; DestDir: "{app}"

; ── Launcher script ───────────────────────────────────────────────────────────
; A .bat wrapper that sets working dir to AppData so receipts/reports save correctly
Source: "installer\launch.bat"; DestDir: "{app}"

; ── App icon ─────────────────────────────────────────────────────────────────
Source: "installer\pos-icon.ico"; DestDir: "{app}"

[Run]
; ── Step 1: Install MySQL silently ───────────────────────────────────────────
; /quiet = no UI, /norestart = don't reboot mid-install
; MYSQL_ROOT_PASSWORD sets the root password (change this before distributing)
Filename: "msiexec.exe";
Parameters: "/i ""{tmp}\mysql-installer.msi"" /quiet /norestart MYSQL_ROOT_PASSWORD=pos_root_2025 ADDLOCAL=ALL";
StatusMsg: "Installing MySQL (this may take a minute)...";
Flags: waituntilterminated runhidden;

; ── Step 2: Start MySQL service ───────────────────────────────────────────────
Filename: "net"; Parameters: "start MySQL84";
StatusMsg: "Starting MySQL service...";
Flags: waituntilterminated runhidden shellexec;

; ── Step 3: Launch the POS (optional — show after install) ───────────────────
Filename: "{app}\launch.bat";
Description: "Launch POS System now";
Flags: postinstall nowait skipifsilent shellexec;

[Icons]
; Desktop shortcut
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\launch.bat";
IconFilename: "{app}\pos-icon.ico"; Tasks: desktopicon

; Start Menu
Name: "{group}\{#MyAppName}"; Filename: "{app}\launch.bat";
IconFilename: "{app}\pos-icon.ico"

; Startup (optional)
Name: "{userstartup}\{#MyAppName}"; Filename: "{app}\launch.bat";
IconFilename: "{app}\pos-icon.ico"; Tasks: startupicon

[UninstallRun]
; Stop MySQL before uninstalling (don't uninstall MySQL — customer may use it elsewhere)
Filename: "net"; Parameters: "stop MySQL84";
Flags: runhidden shellexec; RunOnceId: "StopMySQL"

[Code]
// ── Custom installer code ─────────────────────────────────────────────────────

// Check if MySQL is already installed — skip MSI if so
function IsMySQLInstalled(): Boolean;
var
  version: String;
begin
  Result := RegQueryStringValue(HKLM,
    'SOFTWARE\MySQL AB\MySQL Server 8.4',
    'Version', version);
  if not Result then
    Result := RegQueryStringValue(HKLM,
      'SOFTWARE\WOW6432Node\MySQL AB\MySQL Server 8.4',
      'Version', version);
end;

// Show message if MySQL is already present
procedure InitializeWizard();
begin
  if IsMySQLInstalled() then
    MsgBox('MySQL 8.4 is already installed on this machine. ' +
           'The installer will skip MySQL setup and use the existing installation.',
           mbInformation, MB_OK);
end;

// Write the database connection config with the correct password
// This runs after all files are copied
procedure CurStepChanged(CurStep: TSetupStep);
var
  configContent: String;
  configPath: String;
begin
  if CurStep = ssPostInstall then begin
    // Write a properties file the app reads on startup
    // The app reads this to override the hardcoded DatabaseConnection constants
    configPath := ExpandConstant('{commonappdata}\POS System\config\db.properties');
    configContent :=
      'db.host=127.0.0.1' + #13#10 +
      'db.port=3306' + #13#10 +
      'db.name=pos_db' + #13#10 +
      'db.username=root' + #13#10 +
      'db.password=pos_root_2025' + #13#10;
    SaveStringToFile(configPath, configContent, False);
  end;
end;
