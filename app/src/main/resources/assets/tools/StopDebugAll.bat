@echo off

tasklist | find /i "DayZDiag_x64.exe">nul && Taskkill /F /IM  "DayZDiag_x64.exe"