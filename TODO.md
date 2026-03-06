# TODO

## 1. "Claude Terminal" missing from "Open In" context menu

**Status:** Fixed.

**Root cause:** Missing `getActionUpdateThread()` override. Since IntelliJ 2024.1, `RevealGroup` processes child action `update()` calls on the background thread (BGT). Actions that don't explicitly declare BGT support default to EDT and are silently skipped by BGT-enabled parent groups. The action was registered correctly, instantiated correctly, but never got its `update()` called — so `isEnabledAndVisible` was never set to `true`.

**Fix:** Added `override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT` to `OpenWithClaudeTerminalAction`. The `update()` method only reads data keys (`CommonDataKeys.VIRTUAL_FILE`), which is safe on BGT.

---

## 2. Tool window sidebar icon badge not showing

**Status:** Fixed.

**Root causes:**
1. **No `icon` attribute in `<toolWindow>` XML.** The sidebar stripe had no base icon from registration, forcing a runtime fallback to `AllIcons.Nodes.Console`. This made `setIcon()` behavior unreliable in the new UI, which expects the icon to be declared declaratively.
2. **Custom `BadgedIcon` mixed coordinate spaces.** It used `JBUI.scale()` for badge dimensions (physical scaled pixels) but `baseIcon.iconWidth/Height` for positioning (logical unscaled pixels). At any non-1x HiDPI scale, the badge was mispositioned or clipped. Additionally, the new UI stripe renders icons through `ScalableIcon` internals that don't interact correctly with raw `Graphics2D` compositing in custom `Icon` implementations.

**Fix:**
- Added `icon="AllIcons.Nodes.Console"` to the `<toolWindow>` XML registration so the stripe has a proper base icon from the start.
- Replaced the broken custom `BadgedIcon` class with IntelliJ's `LayeredIcon`, which handles HiDPI scaling, stripe clipping, and icon compositing correctly through the platform's own rendering pipeline.
- Removed the `AllIcons.Nodes.Console` fallback hack from `ClaudeTerminalToolWindowFactory` (no longer needed with the XML attribute).
- Made `DotIcon` parameterized (iconSize, dotSize) so it serves both as tab icon and as the small badge dot for `LayeredIcon` overlay.

---

## Revert notes

Previous debug artifacts have been cleaned up:
- `OpenWithClaudeTerminalAction.kt` — `DumbAwareAction` kept (correct), added `ActionUpdateThread.BGT`
- `ClaudeTerminalToolWindowFactory.kt` — removed `AllIcons.Nodes.Console` fallback, icon now declared in XML
- `ClaudeTerminalStatusTracker.kt` — badge logic kept as-is, works correctly now that `setIcon()` receives a proper `LayeredIcon`
- `SessionIconProvider.kt` — removed `BadgedIcon` and `JBColor` outline, replaced with `LayeredIcon` + parameterized `DotIcon`
