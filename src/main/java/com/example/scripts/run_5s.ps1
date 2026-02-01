$ErrorActionPreference = "Stop"

Write-Host "=== RUN 5s: starting server ==="

# 1) Pornește serverul (în background)
$server = Start-Process -FilePath ".\gradlew.bat" `
  -ArgumentList "runServer5" `
  -PassThru `
  -NoNewWindow

# 2) Așteaptă până serverul deschide portul 5050
$maxWaitSec = 20
$ready = $false
for ($i=0; $i -lt $maxWaitSec; $i++) {
  try {
    $test = Test-NetConnection -ComputerName "127.0.0.1" -Port 5050 -WarningAction SilentlyContinue
    if ($test.TcpTestSucceeded) { $ready = $true; break }
  } catch {}
  Start-Sleep -Seconds 1
}
if (-not $ready) {
  Write-Host "Server did not open port in time. Killing server process..."
  Stop-Process -Id $server.Id -Force
  exit 1
}

Write-Host "=== RUN 5s: server ready, starting clients ==="

# 3) Pornește clienții (foreground -> așteptăm să termine)
# Clienții se vor opri când serverul trimite SERVER_SHUTDOWN / închide conexiunea
& .\gradlew.bat runClients5

Write-Host "=== RUN 5s: clients finished, waiting server ==="

# 4) Așteaptă serverul să se termine (după 180s)
Wait-Process -Id $server.Id

Write-Host "=== RUN 5s: done ==="
