#
# Claude Code Status Tracker Hook (PowerShell)
#
# Native Windows alternative to the bash hook script.
# Reads session JSON from stdin (provided by Claude Code hooks),
# writes a status file per session to ~/.claude/terminal-status/.
#
# Arguments:
#   $args[0] - Target state: "working", "waiting", "idle", or "delete"
#

param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$State
)

$ErrorActionPreference = "Stop"

$StatusDir = Join-Path $env:USERPROFILE ".claude\terminal-status"
if (-not (Test-Path $StatusDir)) {
    New-Item -ItemType Directory -Path $StatusDir -Force | Out-Null
}

# Read JSON from stdin
$Input = [Console]::In.ReadToEnd()

# Extract fields using .NET JSON parsing (available in all PowerShell 5.1+)
try {
    Add-Type -AssemblyName System.Web.Extensions -ErrorAction SilentlyContinue
    $serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
    $parsed = $serializer.DeserializeObject($Input)
    $SessionId = if ($parsed.ContainsKey("session_id")) { $parsed["session_id"] } else { "unknown" }
    $Cwd = if ($parsed.ContainsKey("cwd")) { $parsed["cwd"] } else { "" }
} catch {
    # Fallback: regex extraction
    if ($Input -match '"session_id"\s*:\s*"([^"]*)"') { $SessionId = $Matches[1] } else { $SessionId = "unknown" }
    if ($Input -match '"cwd"\s*:\s*"([^"]*)"') { $Cwd = $Matches[1] } else { $Cwd = "" }
}

$TabId = if ($env:CLAUDE_TERMINAL_TAB_ID) { $env:CLAUDE_TERMINAL_TAB_ID } else { "" }
$StatusFile = Join-Path $StatusDir "$SessionId.json"

if ($State -eq "delete") {
    if (Test-Path $StatusFile) { Remove-Item $StatusFile -Force }
    exit 0
}

# Build JSON with proper escaping via .NET
$Project = if ($Cwd) { Split-Path $Cwd -Leaf } else { "" }
$Timestamp = [long](Get-Date -UFormat %s)

# Use a hashtable and ConvertTo-Json for safe serialization
$statusObj = @{
    session_id = $SessionId
    state      = $State
    cwd        = $Cwd
    project    = $Project
    tab_id     = $TabId
    timestamp  = $Timestamp
}

$json = $statusObj | ConvertTo-Json -Compress

# Atomic write via temp file + move
$TempFile = "$StatusFile.tmp"
$json | Out-File -FilePath $TempFile -Encoding utf8 -NoNewline
Move-Item -Path $TempFile -Destination $StatusFile -Force
