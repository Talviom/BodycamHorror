# Собирает архив сборки для друзей.
# Запуск:  powershell -ExecutionPolicy Bypass -File pack\make-pack.ps1
# Результат: D:\Minecraft\dist\BodycamHorror-pack-<дата>.zip
param(
    [string]$Game = "D:\Minecraft\game",
    [string]$Out = "D:\Minecraft\dist"
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$zip = Join-Path $Out "BodycamHorror-pack-$stamp.zip"

# Что входит в сборку. Миры, логи, аккаунты и личные файлы лаунчера не входят.
$items = @("mods", "config", "defaultconfigs", "tacz", "shaderpacks") |
    Where-Object { Test-Path (Join-Path $Game $_) }

# Временная папка для файлов, которые готовим отдельно: INSTALL.txt и options.txt.
$stage = Join-Path $env:TEMP "bh-pack-$stamp"
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item (Join-Path $PSScriptRoot "INSTALL.txt") (Join-Path $stage "INSTALL.txt") -Force
$staged = @("INSTALL.txt")

# options.txt хоста берём как основу (графика, дальность прорисовки, клавиши сборки),
# но личное сбрасываем: громкость, чувствительность мыши, поле зрения, последний сервер.
$hostOptions = Join-Path $Game "options.txt"
if (Test-Path $hostOptions) {
    $reset = @{
        "mouseSensitivity" = "0.5"
        "fov" = "0.0"
        "soundCategory_master" = "1.0"
        "soundCategory_music" = "0.5"
    }
    $lines = foreach ($line in [System.IO.File]::ReadAllLines($hostOptions)) {
        $key = ($line -split ":", 2)[0]
        if ($key -eq "lastServer") { continue }
        if ($reset.ContainsKey($key)) { "${key}:$($reset[$key])"; continue }
        if ($key -like "soundCategory_*") { "${key}:1.0"; continue }
        $line
    }
    [System.IO.File]::WriteAllLines((Join-Path $stage "options.txt"), [string[]]$lines, (New-Object System.Text.UTF8Encoding $false))
    $staged += "options.txt"
}

Write-Host "Упаковываю: $(($items + $staged) -join ', ')"
& tar.exe -a -c -f $zip -C $Game @items -C $stage @staged
$code = $LASTEXITCODE
Remove-Item $stage -Recurse -Force
if ($code -ne 0) { throw "tar завершился с ошибкой $code" }

$size = [math]::Round((Get-Item $zip).Length / 1MB)
Write-Host "Готово: $zip ($size МБ)"
