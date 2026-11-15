@echo off
setlocal
cd /d "%~dp0"
title Settlr - reset the demo data
echo.
echo   Settlr: reset the demo data
echo   ---------------------------
echo.
if not exist "resources\config\db.properties" goto noconfig
set "JAVA=java"
where java >/dev/null 2>nul
if not errorlevel 1 goto run
if not defined JAVA_HOME goto nojava
set "JAVA=%JAVA_HOME%\bin\java"
:run
"%JAVA%" -cp "lib\*" src\com\settlr\tools\ResetDemo.java
if errorlevel 1 goto vscode
echo.
pause
exit /b 0
:noconfig
echo resources\config\db.properties is missing.
echo Copy db.properties.example to db.properties and put in your MySQL password first.
echo.
pause
exit /b 1
:nojava
echo Java was not found on this computer.
:vscode
echo.
echo Could not run the reset from here. Instead, open
echo src\com\settlr\tools\ResetDemo.java in VS Code and click Run above main.
echo.
pause
exit /b 1
