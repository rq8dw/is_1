# Сборка отчёта: report.template.html -> report.html -> report.pdf (печать через headless Chrome).
#
# Пример:
#   powershell -ExecutionPolicy Bypass -File docs/report/build-report.ps1 `
#       -Student "Иванов Иван Иванович" -Group "P3312" -Teacher "Петров П. П." -Variant "123" `
#       -RepoUrl "https://github.com/user/is-lab1"
#
# Если передан -PlantUmlJar, диаграммы docs/uml/*.puml предварительно перерисовываются в SVG.

param(
    [string]$Student = '____________________',
    [string]$Group = '________',
    [string]$Teacher = '____________________',
    [string]$Variant = '________',
    [string]$RepoUrl = '',
    [string]$PlantUmlJar = '',
    [string]$Chrome = ''
)

$ErrorActionPreference = 'Stop'
$reportDir = $PSScriptRoot
$root = (Resolve-Path (Join-Path $reportDir '..\..')).Path
$utf8 = New-Object System.Text.UTF8Encoding $false

function Encode([string]$text) {
    return [System.Net.WebUtility]::HtmlEncode($text)
}

# ---------- диаграммы ----------
if ($PlantUmlJar) {
    $pumls = Get-ChildItem (Join-Path $root 'docs\uml') -Filter '0*.puml' | ForEach-Object { $_.FullName }
    & java -jar $PlantUmlJar -charset UTF-8 -tsvg -o . @pumls
    if ($LASTEXITCODE -ne 0) { throw 'PlantUML завершился с ошибкой' }
}

# ---------- листинги ----------
function Files([string]$dir, [string]$filter) {
    $path = Join-Path $root $dir
    return Get-ChildItem $path -Filter $filter -File | Sort-Object Name | ForEach-Object { $_.FullName }
}

$java = 'src\main\java\ru\itmo\is\lab1'
$static = 'src\main\resources\static'
$sources = @(
    (Join-Path $root 'build.gradle'),
    (Join-Path $root 'settings.gradle'),
    (Join-Path $root 'src\main\resources\application.yml')
)
$sources += Files 'src\main\resources\db\migration' '*.sql'
$sources += Files $java '*.java'
foreach ($package in 'domain', 'dto', 'repository', 'service', 'service\exception', 'realtime', 'web') {
    $sources += Files "$java\$package" '*.java'
}
$sources += Files $static '*.html'
$sources += Files "$static\js" '*.js'
$sources += Files "$static\js\pages" '*.js'
$sources += Files "$static\css" '*.css'

$listings = New-Object System.Text.StringBuilder
$n = 0
foreach ($file in $sources) {
    $n++
    $relative = $file.Substring($root.Length + 1).Replace('\', '/')
    $code = [IO.File]::ReadAllText($file, $utf8).TrimEnd()
    [void]$listings.AppendLine("<div class=`"listing-title`">Листинг А.$n — $(Encode $relative)</div>")
    [void]$listings.AppendLine("<pre class=`"listing`">$(Encode $code)</pre>")
}

# ---------- подстановка в шаблон ----------
$repo = if ($RepoUrl) { "<code>$(Encode $RepoUrl)</code>" } else { '____________________' }
$html = [IO.File]::ReadAllText((Join-Path $reportDir 'report.template.html'), $utf8)
$html = $html.Replace('{{STUDENT}}', (Encode $Student)).
        Replace('{{GROUP}}', (Encode $Group)).
        Replace('{{TEACHER}}', (Encode $Teacher)).
        Replace('{{VARIANT}}', (Encode $Variant)).
        Replace('{{YEAR}}', [string](Get-Date).Year).
        Replace('{{REPO}}', $repo).
        Replace('{{LISTINGS}}', $listings.ToString())
$htmlPath = Join-Path $reportDir 'report.html'
[IO.File]::WriteAllText($htmlPath, $html, $utf8)

# ---------- печать в PDF ----------
if (-not $Chrome) {
    $Chrome = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $Chrome) { throw 'Не найден Chrome или Edge: укажите путь параметром -Chrome' }

$pdfPath = Join-Path $reportDir 'report.pdf'
$profileDir = Join-Path $env:TEMP 'is-lab1-report-chrome'
$url = ([Uri]$htmlPath).AbsoluteUri
$process = Start-Process -FilePath $Chrome -Wait -PassThru -WindowStyle Hidden -ArgumentList @(
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--no-pdf-header-footer', "--user-data-dir=$profileDir", "--print-to-pdf=$pdfPath", $url)
if (-not (Test-Path $pdfPath)) { throw "PDF не создан (код завершения браузера: $($process.ExitCode))" }

Write-Host "Готово: $pdfPath ($n листингов)"
