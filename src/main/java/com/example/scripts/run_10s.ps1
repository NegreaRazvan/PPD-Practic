$ErrorActionPreference = "Stop"

Write-Host "=== RUN 10s: starting server ==="

$server = Start-Process -FilePath ".\gradlew.bat" `
  -ArgumentList "runServer10" `
  -PassThru `
  -NoNewWindow

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

Write-Host "=== RUN 10s: server ready, starting clients ==="

& .\gradlew.bat runClients10

Write-Host "=== RUN 10s: clients finished, waiting server ==="

Wait-Process -Id $server.Id

Write-Host "=== RUN 10s: done ==="
