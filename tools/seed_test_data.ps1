param(
    [string]$Serial = '127.0.0.1:16448',
    [ValidateSet('rich', 'zero', 'near', 'overtime', 'alert', 'stopped', 'clean')]
    [string]$State = 'rich',
    [string]$ShotDir = '',
    [switch]$NoStart
)

$ErrorActionPreference = 'Stop'
$adb = 'H:\SedentaryReminder\BuildEnv\sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & $adb -s $Serial @AdbArgs
}

$today = ((Invoke-Adb shell date +%Y%m%d) | Out-String).Trim()
$hourRaw = ((Invoke-Adb shell date +%H) | Out-String).Trim()
$hour = 12
if ($hourRaw -match '^\d{1,2}$') { $hour = [int]$hourRaw }
$nowSec = [long]((Invoke-Adb shell date +%s) | Out-String).Trim()
if ($nowSec -le 0) { $nowSec = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }
$nowMs = $nowSec * 1000L
$nowDate = [DateTimeOffset]::FromUnixTimeSeconds($nowSec).DateTime
$today = $nowDate.ToString('yyyyMMdd')

# Resolve app uid (adbd is root on the target emulators)
$pkgOut = ((Invoke-Adb shell cmd package list packages -U com.sedentary.reminder) | Out-String).Trim()
$uid = '10051'
$m = [regex]::Match($pkgOut, 'uid:(\d+)')
if ($m.Success) { $uid = $m.Groups[1].Value }
else {
    $dump = ((Invoke-Adb shell dumpsys package com.sedentary.reminder) | Out-String)
    $m = [regex]::Match($dump, 'userId=(\d+)')
    if ($m.Success) { $uid = $m.Groups[1].Value }
}

$sb = [System.Text.StringBuilder]::new(200000)
[void]$sb.AppendLine("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
[void]$sb.AppendLine('<map>')

function Add-Bool([string]$name, [bool]$value) {
    $v = if ($value) { 'true' } else { 'false' }
    [void]$sb.AppendLine("    <boolean name=`"$name`" value=`"$v`" />")
}
function Add-Int([string]$name, [int]$value) {
    [void]$sb.AppendLine("    <int name=`"$name`" value=`"$value`" />")
}
function Add-Long([string]$name, [long]$value) {
    [void]$sb.AppendLine("    <long name=`"$name`" value=`"$value`" />")
}

# ---------- profile / settings ----------
Add-Bool 'onboarded' $true
Add-Bool 'algoV2' $true
Add-Bool 'autoAdaptive' $true
Add-Bool 'quietEnabled' $true
Add-Int 'quietStart' 23
Add-Int 'quietEnd' 8
Add-Int 'sit' 40
Add-Int 'win' 2
Add-Int 'steps' 120
Add-Int 'repeat' 5
Add-Int 'age' 30
Add-Int 'gender' 0
Add-Int 'height' 170
Add-Int 'weight' 65
Add-Int 'bodyFat' 0
Add-Int 'job' 0
Add-Bool 'healthFlag' $false

# ---------- historical data (skipped for clean state) ----------
$last7RespOk = 0
$last7RespMiss = 0
$currentDaypart = 0
if ($hour -ge 6 -and $hour -lt 12) { $currentDaypart = 1 }
elseif ($hour -ge 12 -and $hour -lt 18) { $currentDaypart = 2 }
elseif ($hour -ge 18) { $currentDaypart = 3 }

if ($State -ne 'clean') {
    # ---------- 365 days of daily statistics ----------
    for ($off = 364; $off -ge 0; $off--) {
        $d = $nowDate.AddDays(-$off)
        $key = $d.ToString('yyyyMMdd')
        $dow = [int]$d.DayOfWeek
        $weekend = ($dow -eq 0 -or $dow -eq 6)

        if ($off -eq 0) { $breaks = 3; $alerts = 1 }
        elseif ($off -le 3) { $breaks = 4; $alerts = 3 }
        else {
            $bucket = ($off * 37) % 12
            if ($bucket -eq 0) { $breaks = 0; $alerts = 0 }
            elseif ($weekend) { $breaks = 1 + (($off * 3) % 3); $alerts = $breaks + ($off % 2) }
            else { $breaks = 2 + (($off * 7) % 4); $alerts = $breaks + (($off * 5) % 4) }
        }

        $respOk = [int]([Math]::Floor($alerts * 0.7))
        $respMiss = $alerts - $respOk
        $sitSec = $breaks * (30 + ($off % 6) * 5) * 60

        Add-Int "t_${key}_breaks" $breaks
        Add-Int "t_${key}_alerts" $alerts
        Add-Int "t_${key}_respOk" $respOk
        Add-Int "t_${key}_respMiss" $respMiss
        Add-Int "t_${key}_sitSumSec" $sitSec
        Add-Int "t_${key}_sitCount" $breaks

        if ($off -lt 7) {
            $last7RespOk += $respOk
            $last7RespMiss += $respMiss
        }
    }

    # ---------- adaptive snapshots for the last 7 days ----------
    $targets = @(30, 35, 40, 45, 50, 40, 35)
    $scores  = @(3, 5, 8, 6, 7, 4, 9)
    $steps7  = @(80, 100, 120, 130, 110, 100, 90)
    for ($i = 0; $i -lt 7; $i++) {
        $key = $nowDate.AddDays(-(6 - $i)).ToString('yyyyMMdd')
        Add-Int "adp_${key}_target" $targets[$i]
        Add-Int "adp_${key}_score" $scores[$i]
        Add-Int "adp_${key}_steps" $steps7[$i]
    }

    # ---------- hour / daypart scores ----------
    for ($h = 0; $h -lt 24; $h++) {
        if ($h -ge 0 -and $h -le 5) { $v = 2 + ($h % 3) }
        elseif ($h -ge 9 -and $h -le 11) { $v = 7 + ($h % 2) }
        elseif ($h -ge 14 -and $h -le 16) { $v = 7 + (($h + 1) % 3) }
        else { $v = 4 + ($h % 3) }
        Add-Int "hscore_$h" $v
    }

    Add-Int 'dscore_0' 3
    Add-Int 'dscore_1' 7
    Add-Int 'dscore_2' 6
    Add-Int 'dscore_3' 4
    Add-Int "dscore_$currentDaypart" 6

    # Keep last-7 compliance near 70% so effectiveSitMinutes stays at sit=40.
    if (($last7RespOk + $last7RespMiss) -lt 5) {
        Add-Int "t_${today}_respOk" 5
        Add-Int "t_${today}_respMiss" 2
    }
    Add-Int 'pressure' 6
}
else {
    Add-Int 'pressure' 5
}

Add-Int 'alertLevel' 0
Add-Int 'lastAlertHour' $hour
Add-Long 'lastPreAlert' 0

# ---------- runtime state scenario ----------
$enabled = $true
$lastBreak = $nowMs
$lastAlert = 0L
switch ($State) {
    'stopped' {
        $enabled = $false
        $lastBreak = $nowMs
        $lastAlert = 0
    }
    'zero' {
        $enabled = $true
        $lastBreak = $nowMs
        $lastAlert = 0
    }
    'near' {
        $enabled = $true
        $lastBreak = $nowMs - 33 * 60000L
        $lastAlert = 0
    }
    'overtime' {
        $enabled = $true
        $lastBreak = $nowMs - 55 * 60000L
        $lastAlert = 0
    }
    'alert' {
        $enabled = $true
        $lastBreak = $nowMs - 55 * 60000L
        $lastAlert = $nowMs - 2 * 60000L
    }
    'clean' {
        $enabled = $false
        $lastBreak = $nowMs
        $lastAlert = 0
    }
    default {
        $enabled = $true
        $lastBreak = $nowMs
        $lastAlert = 0
    }
}
Add-Bool 'enabled' $enabled
Add-Long 'lastBreak' $lastBreak
Add-Long 'lastAlert' $lastAlert

[void]$sb.AppendLine('</map>')

$localDir = Join-Path $env:TEMP 'sed-seed'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
$serialSafe = ($Serial -replace '[^A-Za-z0-9._-]', '_')
$xmlPath = Join-Path $localDir "sedentary-$serialSafe-$State.xml"
[System.IO.File]::WriteAllText($xmlPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))

# Stop app before replacing prefs, then push as root.
Invoke-Adb shell am force-stop com.sedentary.reminder | Out-Null
Invoke-Adb push $xmlPath /data/local/tmp/sedentary-seed.xml | Out-Null
$remote = "/data/data/com.sedentary.reminder/shared_prefs/sedentary.xml"
$cmd = "cp /data/local/tmp/sedentary-seed.xml $remote && chmod 660 $remote && chown ${uid}:${uid} $remote"
Invoke-Adb shell $cmd | Out-Null

Write-Host "seeded $State on $Serial (device date=$today hour=$hour uid=$uid)"
Write-Host "local xml: $xmlPath"

if (-not $NoStart) {
    Invoke-Adb shell am start -n com.sedentary.reminder/.MainActivity | Out-Null
    Start-Sleep -Seconds 2
}

if ($ShotDir -ne '') {
    New-Item -ItemType Directory -Force -Path $ShotDir | Out-Null
    $shot = Join-Path $ShotDir "seed_$State.png"
    cmd /c "`"$adb`" -s $Serial exec-out screencap -p > `"$shot`""
    Write-Host "screenshot: $shot"
}
