@echo off
rem Launches the Bayeux Asset Magazine: starts the local server and opens the page.
cd /d "%~dp0"
if not exist node_modules (
  echo First run - installing server dependencies...
  call npm install
)
start "" http://localhost:3000
node server.js
