# Claude Code Status for JetBrains

**Know what Claude is doing without looking at the terminal.**

A JetBrains plugin that adds live status indicators to your Claude Code terminal tabs. Glance at the colored dot, keep coding.

> 🔴 Working &nbsp;&middot;&nbsp; 🟠 Needs input &nbsp;&middot;&nbsp; 🟢 Done &nbsp;&middot;&nbsp; ⚪ Stale

---

## Why

You're vibe coding with Claude in a terminal tab. You switch to your editor. Now you're alt-tabbing back and forth just to check if Claude is done or waiting for you.

This plugin fixes that. One dot. Zero friction.

## Features

- **Live status dots** on each Claude Terminal tab — updated every 500ms
- **Tabbed terminal tool window** — run multiple Claude sessions side by side
- **"Open In → Claude Terminal"** from the project tree context menu
- **Escape key passthrough** — Esc goes to Claude, not the IDE
- Works with **PhpStorm, WebStorm, IntelliJ IDEA**, and all JetBrains 2024.3+ IDEs

## Install

1. Download the [latest release](https://github.com/c-kick/claude-status-plugin/releases) `.zip`
2. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
3. Restart your IDE

## Setup

The plugin reads status from Claude Code [hooks](https://docs.anthropic.com/en/docs/claude-code/hooks). Two things to set up:

### 1. Copy the hook script

```bash
mkdir -p ~/.claude/hooks
cp hooks/claude-status-tracker.sh ~/.claude/hooks/
chmod +x ~/.claude/hooks/claude-status-tracker.sh
```

> **Note:** The default path is `~/.claude/hooks/`. If you keep it elsewhere, update the paths in step 2.

### 2. Configure Claude Code hooks

Merge the hooks from [`hooks/settings.example.json`](hooks/settings.example.json) into your `~/.claude/settings.json`.

If you don't have a `settings.json` yet, just copy the example:

```bash
cp hooks/settings.example.json ~/.claude/settings.json
```

> **Custom path?** If you placed the hook script somewhere other than `~/.claude/hooks/`, update the `command` paths in your `settings.json` to match.

That's it. Start a Claude Code session in the Claude Terminal tab and watch the dot change.

## Compatibility

| Platform | Version |
|----------|---------|
| JetBrains IDEs | 2024.3 – 2025.3.x |
| Claude Code | Any version with hooks support |
| OS | Windows, macOS, Linux |

## License

MIT
