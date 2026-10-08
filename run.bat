@echo off
rem Build if needed, then start. Set your key first:  set ANTHROPIC_API_KEY=sk-ant-...
cd /d "%~dp0"
if not exist target\furniture-detailing.jar call mvn -q -B package -DskipTests
if "%ANTHROPIC_API_KEY%"=="" echo ANTHROPIC_API_KEY is not set: photo analysis will be off.
java -jar target\furniture-detailing.jar
