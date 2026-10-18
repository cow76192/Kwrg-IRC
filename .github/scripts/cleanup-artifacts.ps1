# Runs on GitHub-hosted runners using their bundled gh and GITHUB_TOKEN.
[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository = 'cow76192/Kwrg-IRC',
    [ValidateRange(1, 3650)]
    [int]$Days = 3,
    [string]$ArtifactName = 'Artifacts',
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install GitHub CLI first: https://cli.github.com/'
}
function Invoke-GitHubApi([string]$Method, [string]$Endpoint) {
    $response = & gh api --hostname github.com --method $Method $Endpoint -H 'Accept: application/vnd.github+json'
    if ($LASTEXITCODE -ne 0) { throw "GitHub API failed: $Method $Endpoint" }
    if ($Method -eq 'GET') { return (($response -join "`n") | ConvertFrom-Json) }
}
$cutoff = [DateTimeOffset]::UtcNow.AddDays(-$Days)
$candidates = @()
$page = 1
# Finish pagination BEFORE deleting, so deletions cannot shift later pages.
do {
    $data = Invoke-GitHubApi 'GET' "repos/$Repository/actions/artifacts?per_page=100&page=$page"
    $batch = @($data.artifacts)
    foreach ($artifact in $batch) {
        if ($artifact.name -ceq $ArtifactName -and
            [DateTimeOffset]::Parse($artifact.created_at) -lt $cutoff) {
            $candidates += $artifact
        }
    }
    $page++
} while ($batch.Count -eq 100)
$bytes = ($candidates | Measure-Object -Property size_in_bytes -Sum).Sum
Write-Host "Repository: $Repository; exact name: $ArtifactName"
Write-Host "Cutoff (UTC): $($cutoff.ToString('o'))"
$candidates | Select-Object id, name, created_at, size_in_bytes | Format-Table -AutoSize | Out-Host
Write-Host ('Matched {0} artifacts, {1:N2} MiB (reported archive size).' -f $candidates.Count, ($bytes / 1MB))
if (-not $Execute) {
    Write-Host 'Preview only. Add -Execute to permanently delete these artifacts.'
    return
}
$deleted = 0
foreach ($artifact in $candidates) {
    # Delete artifact ZIPs only; retain workflow runs, logs, caches and releases.
    Invoke-GitHubApi 'DELETE' "repos/$Repository/actions/artifacts/$($artifact.id)"
    $deleted++
    Write-Host "Deleted artifact $($artifact.id)"
}
Write-Host "Done. Deleted $deleted artifacts."
