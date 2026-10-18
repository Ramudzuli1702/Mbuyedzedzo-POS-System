; ============================================================
;  POS System — Inno Setup Installer Script
;  Produces a single .exe that installs:
;    1. Java 22 Runtime (bundled — no internet needed)
;    2. MySQL Installer web bootstrapper (server setup runs inside the POS)
;    3. The POS application JAR
;    4. A desktop shortcut and Start Menu entry
;
;  HOW TO BUILD THIS:
;    1. Download Inno Setup: https://jrsoftware.org/isdl.php
;    2. Download Java 22 JRE: https://adoptium.net (get the .zip, not installer)
;        → Extract to: installer/jre/
;    3. Download mysql-installer-web-community-8.0.46.0.msi from Oracle.
;        Save under installer/ with its original filename.
;        The POS performs server installation/configuration on first launch.
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
Source: "installer\mysql-installer-web-community-8.0.46.0.msi"; DestDir: "{app}\mysql-setup"

; ── Application JAR ───────────────────────────────────────────────────────────
Source: "installer\{#JarName}"; DestDir: "{app}"

; ── Launcher script ───────────────────────────────────────────────────────────
; A .bat wrapper that sets working dir to AppData so receipts/reports save correctly
Source: "installer\launch.bat"; DestDir: "{app}"

; ── App icon ─────────────────────────────────────────────────────────────────
Source: "installer\pos-icon.ico"; DestDir: "{app}"

[Run]
; Launch normally: the POS asks for a password and elevates only its setup helper.
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

; Do not write db.properties, set a shared root password, or stop a shared
; MySQL service here. FirstRunSetup owns database setup for both editions.
