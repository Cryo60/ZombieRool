@echo off
cd /d "%~dp0"
echo Lancer ce fichier avec clic droit ^> Executer en tant qu administrateur.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Configurer-PareFeu.ps1"
pause
