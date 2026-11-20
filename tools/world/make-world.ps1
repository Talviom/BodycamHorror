# Готовый мир для сборки друзей (0.9): «Комплекс» и лобби уже построены, правила мира выставлены.
# Запуск:  powershell -ExecutionPolicy Bypass -File tools\world\make-world.ps1
# Результат: D:\Minecraft\dist\worlds\Bodycam Kompleks 1..3 (make-pack.ps1 кладёт их в saves архива).
# Мир строит выделенный dev-сервер из текущего кода (плоский пустой мир), поэтому запускать после всех правок карты.
param(
    [string]$Out = "D:\Minecraft\dist\worlds",
    [int]$Copies = 3
)
$ErrorActionPreference = "Stop"
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$dir = Join-Path $repo "run-world"
if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
New-Item -ItemType Directory -Force $dir | Out-Null

# Плоский мир из бездны: снаружи здания пусто и темно, мобов нет.
$props = @(
    "eula=true",
    "level-name=world",
    "level-type=minecraft\:flat",
    'generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1}],"biome"\:"minecraft\:the_void"}',
    "generate-structures=false",
    "spawn-monsters=false",
    "spawn-animals=false",
    "spawn-npcs=false",
    "online-mode=false",
    "spawn-protection=0",
    "gamemode=adventure",
    "difficulty=normal",
    "server-port=25599"
)
[System.IO.File]::WriteAllLines((Join-Path $dir "server.properties"), [string[]]$props)
[System.IO.File]::WriteAllLines((Join-Path $dir "eula.txt"), [string[]]@("eula=true"))

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
Push-Location $repo
try {
    & .\gradlew.bat runServer "-PbhServerDir=run-world" "-PbhTest=tools/world/build_world.txt" --console=plain
} finally {
    Pop-Location
}
$log = Join-Path $dir "logs\latest.log"
$done = Select-String $log -Pattern '\[bh-test\] DONE failures=(\d+)' | Select-Object -Last 1
if (-not $done) { throw "сценарий постройки не дошёл до конца, см. $log" }
Write-Host $done.Line
if ($done.Line -notmatch 'failures=0') { throw "постройка с ошибками, см. $log" }

& python -I (Join-Path $PSScriptRoot "finish_world.py") (Join-Path $dir "world") $Out $Copies
if ($LASTEXITCODE -ne 0) { throw "finish_world.py завершился с ошибкой" }
Write-Host "Готово: $Out"
