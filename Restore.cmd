@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Restore.ps1" %*
set result=%errorlevel%
pause
exit /b %result%
