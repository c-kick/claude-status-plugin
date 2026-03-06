# TODO

## 1. "Claude Terminal" missing from "Open In" context menu

**Status:** Broken — action never appears in the "Open In" submenu despite `RevealGroup` registration.

**What we know:**
- Action is registered in `plugin.xml` with `<add-to-group group-id="RevealGroup" anchor="last"/>`
- `RevealGroup` is the correct group — the built-in "Terminal" entry uses the same group
- The action class (`OpenWithClaudeTerminalAction`) loads fine — plugin works otherwise
- Changed to `DumbAwareAction` (matching the built-in Terminal action) — still doesn't show
- Tested on PhpStorm 2025.2.1 (plugin targets platformVersion 2024.3)

**Theories not yet tested:**
- PhpStorm 2025.x may use a custom `ActionGroup` subclass for `RevealGroup` that returns a curated list of actions, ignoring dynamically added ones
- There may be a plugin compatibility issue between the 2024.3 target and 2025.2.1 runtime
- The action class may be failing to instantiate (check `idea.log` for errors)

**Diagnostic steps needed:**
1. Launch PhpStorm with `-Didea.is.internal=true`
2. Enable `Help > Diagnostic Tools > UI Inspector`
3. Right-click a file, hover over "Open In", and inspect the submenu's actual action group ID
4. Check `Help > Show Log in Explorer` → `idea.log` for any errors related to `ClaudeTerminal.OpenWithClaudeTerminal` or `RevealGroup`
5. In `Help > Diagnostic Tools > Activity Monitor`, search for the action ID to confirm it's registered

**Alternative approaches if RevealGroup is broken:**
- Register in whatever group UI Inspector reveals
- Add a standalone context menu entry outside the "Open In" submenu (e.g., directly in `ProjectViewPopupMenu`)
- Use `ActionManager.getInstance().getAction("RevealGroup")` at runtime to programmatically add the action and log the group's actual class

---

## 2. Tool window sidebar icon badge not showing

**Status:** Broken — the tool window icon changes (AllIcons.Nodes.Console fallback activated, confirming `toolWindow.icon` was null) but no status badge ever appears.

**What we know:**
- `toolWindow.icon` is null inside `createToolWindowContent` (no `icon` attribute in XML)
- The fallback to `AllIcons.Nodes.Console` works (icon visually changed), confirming the code path runs
- `baseToolWindowIcon` IS being set (to the Console icon)
- Tab-level status dots work correctly (orange dot visible on Claude Terminal tabs)
- The badge still doesn't appear, meaning `updateToolWindowBadge` either isn't reaching `toolWindow.setIcon()` or the icon renders without a visible badge

**Likely root causes to investigate:**
1. **Tool window stripe icon size**: Sidebar icons in the "new UI" may be rendered at a different size than `AllIcons.Nodes.Console` (13x13). The badge (6px JBUI-scaled dot in bottom-right corner) may be clipped or rendered outside the visible area
2. **`setIcon()` may be ignored**: In the "new UI" (default since 2024.2), `ToolWindow.setIcon()` may not affect the sidebar stripe icon — the icon might be managed by the UI framework and only read once from the XML registration
3. **The `lastBadgeState` optimization may be wrong**: If `lastBadgeState` starts as null and the first `worstState` is also null, the early return fires and `lastBadgeState` never advances — but this should be fine since no badge is needed in that case
4. **Threading**: `updateToolWindowBadge` runs on EDT (via `ClaudeSessionMonitor.notifyListeners` → `invokeLater`). `setIcon` should be safe on EDT.

**Diagnostic steps needed:**
1. Add logging to `updateToolWindowBadge` — log `baseToolWindowIcon`, `worstState`, `lastBadgeState`, and whether `setIcon` is called
2. Test whether `toolWindow.setIcon(SessionIconProvider.getIcon(SessionState.WORKING))` (a plain red dot, not a composite) actually changes the sidebar icon — this isolates whether `setIcon` works at all on the new UI
3. Check if the `icon` attribute in `<toolWindow>` XML is required for the sidebar to respect `setIcon()` calls
4. Inspect whether `BadgedIcon.paintIcon` is ever called (add a log/breakpoint)

**Alternative approaches:**
- Use `toolWindow.setIcon()` with a completely different large obvious icon (e.g., `AllIcons.General.Error`) to test if `setIcon` has any effect at all on the sidebar stripe
- If `setIcon` doesn't work in the new UI, investigate `ToolWindowEx` or `ToolWindowManager` APIs for badge/notification support (IntelliJ 2024.3+ may have a dedicated badge API)
- Use `toolWindow.setTitleActions` or content-level notifications as an alternative attention mechanism

---

## Revert notes

The current code has changes from these two attempts. Before the next attempt:
- `OpenWithClaudeTerminalAction.kt` — changed from `AnAction` to `DumbAwareAction` (keep this, it's correct regardless)
- `ClaudeTerminalToolWindowFactory.kt` — added `AllIcons.Nodes.Console` fallback and explicit `setIcon` (revert the icon fallback once root cause is found)
- `ClaudeTerminalStatusTracker.kt` — added `updateToolWindowBadge` with badge logic, `baseToolWindowIcon`, `lastBadgeState` (keep structure, fix once diagnosis is done)
- `SessionIconProvider.kt` — added `BadgedIcon` class, `getBadgedIcon` with caching, JBColor outline (keep, will work once setIcon issue is resolved)
