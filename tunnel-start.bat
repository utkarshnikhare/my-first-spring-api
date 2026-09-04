@echo off
REM Start cloudflared quick tunnel detached from any shell
cd /d C:\project\my-first-spring-api
if exist tunnel-live.log del /q tunnel-live.log
start "sociomart-tunnel" /b tools\cloudflared.exe tunnel --url http://localhost:8081 --protocol http2 --logfile C:\project\my-first-spring-api\tunnel-live.log --loglevel info
echo tunnel process launched