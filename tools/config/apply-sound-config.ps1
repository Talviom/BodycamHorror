# 0.9 (пункты 20 и 14): настройки звуковых модов в папке игры. Сборка для друзей берёт config из игры,
# поэтому достаточно запустить один раз на ПК хоста (игра должна быть закрыта):
#   powershell -ExecutionPolicy Bypass -File tools\config\apply-sound-config.ps1 [-Game D:\Minecraft\game]
# Перед правкой кладёт копию файла рядом (*.bak_09, в архив не попадает).
#
# Sound Physics Remastered:
#   - эхо голоса: отзвук стен для голоса выключен, свой голос себе не возвращается (simple_voice_chat_*);
#   - шаги понятнее по направлению: звук идёт из места, где он есть, а не из проёма, через который отразился
#     (sound_direction_evaluation=false); за стенами глушится слабее, отзвук короче и тише.
# Presence Footsteps: чужие шаги громче своих, общий уровень выше (свои шаги не мешают слышать чужие).
param([string]$Game = "D:\Minecraft\game")
$ErrorActionPreference = "Stop"

function Set-Props([string]$file, [hashtable]$values) {
    if (-not (Test-Path $file)) { Write-Warning "нет файла $file (запустите игру один раз)"; return }
    Copy-Item $file "$file.bak_09" -Force
    $seen = @{}
    $lines = foreach ($line in [IO.File]::ReadAllLines($file)) {
        $key = ($line -split "=", 2)[0].Trim()
        if ($values.ContainsKey($key)) { $seen[$key] = $true; "$key=$($values[$key])" } else { $line }
    }
    foreach ($k in $values.Keys) { if (-not $seen[$k]) { $lines = @($lines) + "$k=$($values[$k])" } }
    [IO.File]::WriteAllLines($file, [string[]]$lines, (New-Object Text.UTF8Encoding $false))
    Write-Host "обновлён $file"
}

Set-Props (Join-Path $Game "config\sound_physics_remastered\soundphysics.properties") @{
    "simple_voice_chat_integration" = "false"
    "simple_voice_chat_hear_self"   = "false"
    "sound_direction_evaluation"    = "false"
    "default_block_occlusion_factor" = "0.6"
    "reverb_gain"                   = "0.6"
    "reverb_distance"               = "1.0"
}

$pf = Join-Path $Game "config\presencefootsteps\userconfig.json"
if (Test-Path $pf) {
    Copy-Item $pf "$pf.bak_09" -Force
    $j = Get-Content $pf -Raw | ConvertFrom-Json
    $j.volume = 100
    $j.clientPlayerVolume = 55
    $j.otherPlayerVolume = 100
    $j.runningVolumeIncrease = 30
    [IO.File]::WriteAllText($pf, ($j | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding $false))
    Write-Host "обновлён $pf"
} else {
    Write-Warning "нет файла $pf"
}
