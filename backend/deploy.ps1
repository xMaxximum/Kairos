param(
    [string]$ServerAlias = "todo",
    [string]$RemotePath = "/opt/kairos",
    [string]$ComposeFile = "docker-compose.prod.yml",
    [switch]$Logs
)

$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}

function Escape-RemoteSingleQuote {
    param([string]$Value)
    return $Value.Replace("'", "'\''")
}

$backendPath = $PSScriptRoot
$repoPath = Split-Path $backendPath -Parent
$archiveName = "kairos-backend-$([Guid]::NewGuid().ToString('N')).tar.gz"
$localArchive = Join-Path ([IO.Path]::GetTempPath()) $archiveName
$remoteArchive = "/tmp/$archiveName"

$remotePathEscaped = Escape-RemoteSingleQuote $RemotePath
$remoteArchiveEscaped = Escape-RemoteSingleQuote $remoteArchive
$composeFileEscaped = Escape-RemoteSingleQuote $ComposeFile

try {
    Write-Host "Packaging backend..."
    Invoke-Native tar `
        -czf $localArchive `
        --exclude=.env `
        --exclude=backend/.env `
        --exclude=bin `
        --exclude=obj `
        --exclude=node_modules `
        --exclude=.nuxt `
        --exclude=.output `
        --exclude=artifacts `
        --exclude=deploy.ps1 `
        -C $repoPath backend web

    Write-Host "Uploading to $ServerAlias..."
    Invoke-Native scp $localArchive "${ServerAlias}:$remoteArchive"

    Write-Host "Rebuilding and restarting services..."
    $deployCommand = "set -e; mkdir -p '$remotePathEscaped'; cd '$remotePathEscaped'; tar -xzf '$remoteArchiveEscaped'; rm -f '$remoteArchiveEscaped'; find backend web -type d -exec chmod 755 {} +; find backend web -type f -exec chmod 644 {} +; [ ! -f backend/.env ] || chmod 600 backend/.env; cd backend; docker compose -f '$composeFileEscaped' up -d --build --remove-orphans; docker compose -f '$composeFileEscaped' restart caddy; docker compose -f '$composeFileEscaped' ps"
    Invoke-Native ssh $ServerAlias $deployCommand

    if ($Logs) {
        Invoke-Native ssh $ServerAlias "cd '$remotePathEscaped/backend' && docker compose -f '$composeFileEscaped' logs -f api web"
    }
}
finally {
    if (Test-Path $localArchive) {
        Remove-Item -LiteralPath $localArchive -Force
    }

    ssh $ServerAlias "rm -f '$remoteArchiveEscaped'" *> $null
}
