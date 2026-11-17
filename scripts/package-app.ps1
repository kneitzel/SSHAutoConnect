param(
    [Alias('SkipBuild')][switch]$SkipTests,
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $mavenOptions = @('-B', '-ntp')
    if ($SkipTests) { $mavenOptions += '-DskipTests' }
    if ($MavenArguments) { $mavenOptions += $MavenArguments }
    $mavenOptions += 'install'
    & ./mvnw.cmd @mavenOptions
    if ($LASTEXITCODE -ne 0) { throw 'Maven application packaging failed' }
} finally { Pop-Location }
