@echo off
setlocal ENABLEDELAYEDEXPANSION
CALL %appdata%/.dayzworkbenchtool./tools/UserSettings.bat

start /D "%GAME_PATH%" DayZDiag_x64.exe %ServerArgs%