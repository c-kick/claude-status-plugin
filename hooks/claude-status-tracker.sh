#!/usr/bin/env bash
#
# Claude Code Status Tracker Hook
#
# Reads session JSON from stdin (provided by Claude Code hooks),
# writes a status file per session to ~/.claude/terminal-status/.
#
# Arguments:
#   $1 - Target state: "working", "waiting", "idle", or "delete"

set -euo pipefail

STATUS_DIR="${HOME}/.claude/terminal-status"
mkdir -p "${STATUS_DIR}"

INPUT=$(cat)

SESSION_ID=$(echo "${INPUT}" | node -e "
  let d=''; process.stdin.on('data',c=>d+=c);
  process.stdin.on('end',()=>{
    try{console.log(JSON.parse(d).session_id||'unknown')}catch(e){console.log('unknown')}
  });")

CWD=$(echo "${INPUT}" | node -e "
  let d=''; process.stdin.on('data',c=>d+=c);
  process.stdin.on('end',()=>{
    try{console.log(JSON.parse(d).cwd||'')}catch(e){console.log('')}
  });")

STATE="${1:-unknown}"
TAB_ID="${CLAUDE_TERMINAL_TAB_ID:-}"
STATUS_FILE="${STATUS_DIR}/${SESSION_ID}.json"

if [ "${STATE}" = "delete" ]; then rm -f "${STATUS_FILE}"; exit 0; fi

TEMP_FILE="${STATUS_FILE}.tmp"
cat > "${TEMP_FILE}" << EOF
{
  "session_id": "${SESSION_ID}",
  "state": "${STATE}",
  "cwd": "${CWD}",
  "project": "$(basename "${CWD}")",
  "tab_id": "${TAB_ID}",
  "timestamp": $(date +%s)
}
EOF
mv -f "${TEMP_FILE}" "${STATUS_FILE}"
