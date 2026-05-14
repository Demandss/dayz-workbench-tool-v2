@echo off
setlocal ENABLEDELAYEDEXPANSION
CALL %appdata%/.dayzworkbenchtool./tools/UserSettings.bat

tasklist | find /i "workbenchApp.exe">nul && Taskkill /F /IM  "workbenchApp.exe"
start /D "%WORKBENCH_PATH%" workbenchApp.exe "-mod=%LOAD_MODS%"