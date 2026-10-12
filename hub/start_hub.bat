@echo off
rem NoteLink hub launcher — installs Flask on first run, then serves the UI.
cd /d "%~dp0"
python -c "import flask" 2>nul || pip install flask
start "" http://127.0.0.1:8765
python hub.py
pause
