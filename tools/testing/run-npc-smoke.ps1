param(
    [ValidateSet('absent', 'present', 'restart')][string[]]$Profiles = @('absent', 'present', 'restart'),
    [string]$AcceptedEula = 'run/eula.txt'
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $projectRoot
$acceptedText = Get-Content -LiteralPath $AcceptedEula -Raw
if ($acceptedText -notmatch '(?m)^\s*eula\s*=\s*true\s*$') { throw 'Supply an already accepted EULA file with -AcceptedEula.' }
$outputRoot = Join-Path $projectRoot ('dev-runs/npc-smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $outputRoot | Out-Null
foreach ($profile in $Profiles) {
    $runtime = Join-Path $outputRoot $(if ($profile -eq 'restart') { 'present' } else { $profile })
    if ($profile -eq 'restart') {
        if (!(Test-Path -LiteralPath (Join-Path $runtime 'probe-world/npc-probe-orphan.json'))) {
            throw 'Run present before restart in the same invocation.'
        }
    } else {
        New-Item -ItemType Directory -Path $runtime | Out-Null
        Copy-Item -LiteralPath $AcceptedEula -Destination (Join-Path $runtime 'eula.txt')
        @('server-ip=127.0.0.1', 'server-port=25594', 'online-mode=false', 'level-name=probe-world',
          'level-seed=8675309', 'level-type=minecraft:flat', 'view-distance=3', 'simulation-distance=3',
          'spawn-protection=0',
          'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}') |
            Set-Content -LiteralPath (Join-Path $runtime 'server.properties') -Encoding utf8
    }
    $probeLog = Join-Path $outputRoot ($profile + '.log')
    $gradleArgs = @('runServer', "-PnpcProbe=$profile", "-PgameRunDirectory=$runtime", '--offline', '--console=plain')
    if ($profile -ne 'absent') { $gradleArgs += '-PnpcSmoke' }
    & .\gradlew.bat @gradleArgs *> $probeLog
    $result = $LASTEXITCODE
    $content = Get-Content -LiteralPath $probeLog -Raw
    $marker = 'TT_NPC_PROBE_' + $profile.ToUpperInvariant() + '_PASS'
    if ($result -ne 0 -or !$content.Contains($marker) -or $content.Contains('TT_NPC_PROBE_FAIL')) {
        throw "NPC $profile probe failed (exit $result); see $probeLog"
    }
    Write-Output "PASS $profile - $probeLog"
}
