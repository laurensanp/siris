# Uploads build\libs\siris-<Version>+mc26.3.jar to Modrinth (project siris-dlss) and marks it client-only.
# Usage (any folder): powershell -ExecutionPolicy Bypass -File tools\modrinth-upload.ps1 -Version 1.3.4
# Token: MODRINTH_TOKEN=... in the repo's .env (never commit it). Changelog: tools\release-notes-<Version>.md from "## SIRIS".
param([Parameter(Mandatory = $true)][string]$Version)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$token = ((Get-Content "$root\.env" | Where-Object { $_ -match '^MODRINTH_TOKEN=' }) -replace '^MODRINTH_TOKEN=', '').Trim().Trim('"')
if (-not $token) { throw "MODRINTH_TOKEN missing in $root\.env" }
$jar = "$root\mod\build\libs\siris-$Version+mc26.3.jar"
if (-not (Test-Path $jar)) { throw "$jar not found: run gradlew build in mod first" }
$notes = Get-Content "$PSScriptRoot\release-notes-$Version.md" -Raw
$changelog = $notes.Substring($notes.IndexOf("## SIRIS"))

$data = [ordered]@{
  project_id     = "NLzgnD4s"
  name           = "SIRIS $Version"
  version_number = "$Version+mc26.3"
  changelog      = $changelog
  version_type   = "release"
  game_versions  = @("26.3")
  loaders        = @("fabric")
  featured       = $true
  status         = "listed"
  dependencies   = @(
    @{ project_id = "AANobbMI"; dependency_type = "required" }   # Sodium
    @{ project_id = "oSIKhgz3"; dependency_type = "optional" }   # Vitrail
    @{ project_id = "mOgUt4GM"; dependency_type = "optional" }   # Mod Menu
  )
  file_parts     = @("file")
  primary_file   = "file"
}
$dataFile = "$root\mod\build\modrinth-$Version.json"
[IO.File]::WriteAllText($dataFile, ($data | ConvertTo-Json -Depth 5))

$out = curl.exe -sS -X POST "https://api.modrinth.com/v2/version" `
  -H "Authorization: $token" -H "User-Agent: siris-release" `
  -F "data=<$dataFile;type=application/json" `
  -F "file=@$jar;type=application/java-archive"
$resp = $out | ConvertFrom-Json
if (-not $resp.id) { throw "Upload failed, nothing created: $out" }
Write-Host "Created version $($resp.id) ($($resp.version_number), $($resp.status))"

# Client-only is a v3 field. 204 = done; 400 = set "Client-only" on the version page instead.
$patchFile = "$root\mod\build\modrinth-$Version-env.json"
[IO.File]::WriteAllText($patchFile, '{"environment":"client_only"}')
curl.exe -sS -X PATCH "https://api.modrinth.com/v3/version/$($resp.id)" `
  -H "Authorization: $token" -H "User-Agent: siris-release" -H "Content-Type: application/json" `
  --data-binary "@$patchFile" -w "PATCH client_only: %{http_code}`n"
