# Сборка и запуск ИС «Организации» на helios.
# Запуск:  .\deploy\deploy-helios.ps1            - собрать jar, залить, перезапустить, поднять туннель
#          .\deploy\deploy-helios.ps1 -NoBuild   - не пересобирать jar
#          .\deploy\deploy-helios.ps1 -NoTunnel  - только развернуть
#          .\deploy\deploy-helios.ps1 -Tunnel    - только туннель
#          .\deploy\deploy-helios.ps1 -Stop      - остановить приложение на сервере
#          .\deploy\deploy-helios.ps1 -Status    - состояние и последние строки лога
#
# Вход по ключу ~/.ssh/helios_key, пароль к БД берётся на сервере из ~/.pgpass (см. start.sh).

param(
    [switch]$NoBuild,
    [switch]$NoTunnel,
    [switch]$Tunnel,
    [switch]$Stop,
    [switch]$Status
)

$ErrorActionPreference = 'Stop'

# --- параметры -------------------------------------------------------------
$User       = 's467234'
$HostName   = 'helios.cs.ifmo.ru'
$SshPort    = 2222
$KeyFile    = "$env:USERPROFILE\.ssh\helios_key"
$AppPort    = 24672                      # порт приложения на helios и локальный порт туннеля
$RemoteDir  = 'is-lab1'                  # относительно домашнего каталога
$ProjectDir = Split-Path -Parent $PSScriptRoot
# ---------------------------------------------------------------------------

$Jar     = Join-Path $ProjectDir 'build\libs\is-lab1.jar'
$SshArgs = @('-i', $KeyFile, '-p', $SshPort, '-o', 'BatchMode=yes', "$User@$HostName")

# helios иногда обрывает SSH-подключения (kex_exchange_identification, lost connection),
# поэтому при ошибке соединения пробуем ещё раз после паузы.
# ssh сообщает об обрыве кодом 255, scp — кодом 1.
function Invoke-WithRetry([scriptblock]$Action, [int[]]$RetryCodes = @(255)) {
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        $output = & $Action
        if ($RetryCodes -notcontains $LASTEXITCODE) {
            return $output
        }
        Write-Host "  (соединение отклонено, повтор через $($attempt * 5) с)"
        Start-Sleep -Seconds ($attempt * 5)
    }
    throw 'Не удалось подключиться к helios'
}

function Invoke-Remote([string]$Command) {
    Invoke-WithRetry { ssh @SshArgs $Command }
}

if (-not (Test-Path $KeyFile)) {
    throw "Не найден ключ $KeyFile. Вход по ключу не настроен."
}

if ($Status) {
    Invoke-Remote "pgrep -U $User -f $RemoteDir/is-lab1.jar >/dev/null && echo 'Приложение: работает' || echo 'Приложение: остановлено'; tail -5 ~/$RemoteDir/app.log 2>/dev/null"
    exit 0
}

if ($Stop) {
    Invoke-Remote "pkill -U $User -f $RemoteDir/is-lab1.jar && echo 'остановлено' || echo 'не было запущено'"
    exit 0
}

function Start-Tunnel {
    Write-Host "=== Туннель на $HostName ==="
    Write-Host ''
    Write-Host "  Приложение: http://localhost:$AppPort/"
    Write-Host ''
    Write-Host 'Туннель держится, пока открыто это окно. Ctrl+C — разорвать.'
    ssh -i $KeyFile -p $SshPort -N -o ServerAliveInterval=30 -L "${AppPort}:localhost:$AppPort" "$User@$HostName"
}

if ($Tunnel) {
    Start-Tunnel
    exit 0
}

Write-Host '=== Развёртывание ИС «Организации» на helios ==='

# 1. Сборка
if (-not $NoBuild) {
    Write-Host ''
    Write-Host '--- Сборка jar ---'
    Push-Location $ProjectDir
    try {
        $gradle = if (Get-Command gradle -ErrorAction SilentlyContinue) { 'gradle' } else { '.\gradlew.bat' }
        & $gradle bootJar --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с ошибкой ($LASTEXITCODE)" }
    } finally { Pop-Location }
}
if (-not (Test-Path $Jar)) {
    throw "Не найден $Jar. Запусти без -NoBuild, чтобы собрать."
}

# 2. Загрузка
Write-Host ''
Write-Host "--- Загрузка jar ($([math]::Round((Get-Item $Jar).Length / 1MB)) МБ) ---"
Invoke-Remote "mkdir -p ~/$RemoteDir" | Out-Null
Invoke-WithRetry { scp -i $KeyFile -P $SshPort -q $Jar (Join-Path $PSScriptRoot 'start.sh') "${User}@${HostName}:$RemoteDir/" } @(1, 255) | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Не удалось загрузить файлы' }
Write-Host 'загружено'

# 3. Запуск и ожидание старта одним подключением (start.sh сам останавливает предыдущий экземпляр)
Write-Host ''
Write-Host '--- Запуск (до 2 минут) ---'
$log = "~/$RemoteDir/app.log"
$startAndWait = "sh ~/$RemoteDir/start.sh $AppPort; i=0; state=WAIT; " +
        "while [ `$i -lt 40 ]; do sleep 3; i=`$((i+1)); " +
        "if grep -q 'Started IsLab1Application' $log; then state=OK; break; fi; " +
        "if ! pgrep -U $User -f $RemoteDir/is-lab1.jar >/dev/null; then state=FAIL; break; fi; done; " +
        "if [ `$state = OK ]; then grep -aE 'Successfully applied|Tomcat started|Started IsLab1Application' $log | tail -3; " +
        "else tail -40 $log; fi; echo STATE=`$state"
$output = Invoke-Remote $startAndWait
$output | Where-Object { $_ -notmatch '^STATE=' } | ForEach-Object { Write-Host $_ }
if (-not ($output -match '^STATE=OK$')) {
    Write-Host ''
    Write-Host 'Запустить не удалось (см. лог выше).'
    exit 1
}

# 5. Туннель
if ($NoTunnel) {
    Write-Host ''
    Write-Host 'Развёрнуто. Чтобы открыть в браузере, подними туннель:'
    Write-Host '  .\deploy\deploy-helios.ps1 -Tunnel'
    exit 0
}
Write-Host ''
Start-Tunnel
