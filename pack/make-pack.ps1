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
        # Чёрный экран загрузки Forge и Minecraft вместо красного (мод включает его и сам, но только со второго запуска).
        "darkMojangStudiosBackground" = "true"
        # Меню голосового чата на 0: V занята ударом прикладом TACZ, B рюкзаком ZeroContact, свободен только 0.
        "key_key.voice_chat" = "key.keyboard.0"
        # Z (TACZ: осмотр и обвесы) в матче не нужен: оружие выдаётся готовым. Экран обвесов мод и так не открывает.
        "key_key.tacz.refit.desc" = "key.keyboard.unknown"
    }
    $lines = foreach ($line in [System.IO.File]::ReadAllLines($hostOptions)) {
        $key = ($line -split ":", 2)[0]
        if ($key -eq "lastServer") { continue }
        if ($reset.ContainsKey($key)) { "${key}:$($reset[$key])"; continue }
        if ($key -like "soundCategory_*") { "${key}:1.0"; continue }
        $line
    }
    if (-not ($lines | Where-Object { $_ -like "darkMojangStudiosBackground:*" })) { $lines = @($lines) + "darkMojangStudiosBackground:true" }
    if (-not ($lines | Where-Object { $_ -like "key_key.voice_chat:*" })) { $lines = @($lines) + "key_key.voice_chat:key.keyboard.0" }
    [System.IO.File]::WriteAllLines((Join-Path $stage "options.txt"), [string[]]$lines, (New-Object System.Text.UTF8Encoding $false))
    $staged += "options.txt"
}

# Личные файлы хоста в config, которые друзьям не нужны: моды создадут их заново с настройками по умолчанию.
#  - voicechat: кэш имён и UUID игроков, громкости отдельных игроков и категорий, свои настройки микрофона;
#  - sounds/chat.json: слова-упоминания с ником хоста.
$exclude = @(
    "config/voicechat/username-cache.json",
    "config/voicechat/player-volumes.properties",
    "config/voicechat/category-volumes.properties",
    "config/voicechat/voicechat-client.properties",
    "config/sounds/chat.json",
    # Резервные копии конфигов, сделанные при настройке сборки (*.bak, *.bak_0.3 и т. п.).
    "*.bak",
    "*.bak_*"
)
$excludeArgs = foreach ($e in $exclude) { "--exclude"; $e }

Write-Host "Упаковываю: $(($items + $staged) -join ', ')"
& tar.exe -a -c -f $zip @excludeArgs -C $Game @items -C $stage @staged
$code = $LASTEXITCODE
Remove-Item $stage -Recurse -Force
if ($code -ne 0) { throw "tar завершился с ошибкой $code" }

$size = [math]::Round((Get-Item $zip).Length / 1MB)
Write-Host "Готово: $zip ($size МБ)"
