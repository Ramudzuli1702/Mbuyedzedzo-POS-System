@echo off
:: ============================================================
::  POS System — Launcher
::
::  NOTE: When installed via jpackage this .bat is NOT used —
::  jpackage generates its own native launcher (POS.exe).
::  This script is retained for development / manual JAR launches.
::
::  Working directory: %PROGRAMDATA%\POS System
::  This matches where FirstRunSetup writes db.properties and where
::  DatabaseConnection reads it from.
:: ============================================================

:: Working directory — all file saves go here
set WORK_DIR=%PROGRAMDATA%\POS System
if not exist "%WORK_DIR%" mkdir "%WORK_DIR%"
cd /d "%WORK_DIR%"

:: Bundled JRE — use this instead of any system Java
:: (jpackage installs the JRE to the app directory next to this batch file)
set JAVA_EXE=%~dp0runtime\bin\javaw.exe

:: Fall back to system Java if the bundled JRE isn't present (dev mode)
if not exist "%JAVA_EXE%" set JAVA_EXE=javaw.exe

:: Application JAR
set JAR=%~dp0point-of-sale-system-1.0.0.jar

:: Launch
"%JAVA_EXE%" ^
  -Xmx512m ^
  --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED ^
  --add-opens=javafx.base/com.sun.javafx.reflect=ALL-UNNAMED ^
  -Djpackage.app-path=%~dp0POS.exe ^
  -jar "%JAR%"

:: If javaw exits with an error (silent), retry with java.exe so errors
:: are visible in a console window.
if %ERRORLEVEL% NEQ 0 (
    set JAVA_EXE=%~dp0runtime\bin\java.exe
    if not exist "%JAVA_EXE%" set JAVA_EXE=java.exe
    "%JAVA_EXE%" ^
      -Xmx512m ^
      --add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED ^
      --add-opens=javafx.base/com.sun.javafx.reflect=ALL-UNNAMED ^
      -Djpackage.app-path=%~dp0POS.exe ^
      -jar "%JAR%"
    pause
)
