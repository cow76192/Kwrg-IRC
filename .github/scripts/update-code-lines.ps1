$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$sourceFiles = @(& git -C $repositoryRoot ls-files -- 'src/**/*.java')
if ($LASTEXITCODE -ne 0) { throw 'Could not list tracked Java source files' }
if ($sourceFiles.Count -eq 0) { throw 'No tracked Java source files found' }

$lineCount = 0L
foreach ($sourceFile in $sourceFiles) {
    $lineCount += [IO.File]::ReadAllLines((Join-Path $repositoryRoot $sourceFile)).LongLength
}
$displayCount = $lineCount.ToString('N0', [Globalization.CultureInfo]::InvariantCulture)
$badgeCount = [Uri]::EscapeDataString($displayCount)
$labelPattern = '(?<=Java source lines: )[0-9,]+'
$urlPattern = '(?<=https://img\.shields\.io/badge/Code%20lines-)[0-9]+(?:%2C[0-9]+)*'

foreach ($readmeName in @('README.md', 'README.en.md')) {
    $readmePath = Join-Path $repositoryRoot $readmeName
    $original = [IO.File]::ReadAllText($readmePath)
    if ([regex]::Matches($original, $labelPattern).Count -ne 1 -or
        [regex]::Matches($original, $urlPattern).Count -ne 1) {
        throw "Expected exactly one code-line badge in $readmeName"
    }
    $updated = [regex]::Replace($original, $labelPattern, $displayCount)
    $updated = [regex]::Replace($updated, $urlPattern, $badgeCount)
    if ($updated -cne $original) {
        [IO.File]::WriteAllText($readmePath, $updated, [Text.UTF8Encoding]::new($false))
    }
}
Write-Host "Java source lines: $displayCount across $($sourceFiles.Count) tracked files (including comments and blank lines)."
