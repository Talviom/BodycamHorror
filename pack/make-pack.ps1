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
$items = @("mods", "config", "defaultconfigs", "tacz", "shaderpacks", "options.txt") |
    Where-Object { Test-Path (Join-Path $Game $_) }

$install = Join-Path $PSScriptRoot "INSTALL.txt"
Copy-Item $install (Join-Path $Game "INSTALL.txt") -Force
$items += "INSTALL.txt"

Write-Host "Упаковываю: $($items -join ', ')"
& tar.exe -a -c -f $zip -C $Game @items
if ($LASTEXITCODE -ne 0) { throw "tar завершился с ошибкой $LASTEXITCODE" }
Remove-Item (Join-Path $Game "INSTALL.txt") -Force

$size = [math]::Round((Get-Item $zip).Length / 1MB)
Write-Host "Готово: $zip ($size МБ)"
