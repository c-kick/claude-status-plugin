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

# Extract fields from JSON without requiring Node.js or jq.
# Uses grep + sed for simple key-value extraction from flat JSON.
extract_json_field() {
    local field="$1"
    echo "${INPUT}" | grep -o "\"${field}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*:[[:space:]]*"//;s/"$//'
}

SESSION_ID=$(extract_json_field "session_id")
SESSION_ID="${SESSION_ID:-unknown}"

CWD=$(extract_json_field "cwd")
CWD="${CWD:-}"

STATE="${1:-unknown}"
TAB_ID="${CLAUDE_TERMINAL_TAB_ID:-}"
STATUS_FILE="${STATUS_DIR}/${SESSION_ID}.json"

if [ "${STATE}" = "delete" ]; then rm -f "${STATUS_FILE}"; exit 0; fi

# Proper JSON escaping to prevent injection from directory names
# containing quotes, backslashes, or other special characters.
json_escape() {
    local str="$1"
    str="${str//\\/\\\\}"   # backslashes first
    str="${str//\"/\\\"}"   # double quotes
    str="${str//$'\n'/\\n}" # newlines
    str="${str//$'\r'/\\r}" # carriage returns
    str="${str//$'\t'/\\t}" # tabs
    echo -n "${str}"
}

ESCAPED_SESSION_ID=$(json_escape "${SESSION_ID}")
ESCAPED_STATE=$(json_escape "${STATE}")
ESCAPED_CWD=$(json_escape "${CWD}")
ESCAPED_PROJECT=$(json_escape "$(basename "${CWD}")")
ESCAPED_TAB_ID=$(json_escape "${TAB_ID}")

TEMP_FILE="${STATUS_FILE}.tmp"
printf '{"session_id":"%s","state":"%s","cwd":"%s","project":"%s","tab_id":"%s","timestamp":%s}\n' \
    "${ESCAPED_SESSION_ID}" \
    "${ESCAPED_STATE}" \
    "${ESCAPED_CWD}" \
    "${ESCAPED_PROJECT}" \
    "${ESCAPED_TAB_ID}" \
    "$(date +%s)" > "${TEMP_FILE}"
mv -f "${TEMP_FILE}" "${STATUS_FILE}"
