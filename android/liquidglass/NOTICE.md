# AndroidLiquidGlass source attribution

Source: https://github.com/Kyant0/AndroidLiquidGlass
Commit: 65ab177e90e5c1d8c62e70cf7755841982da65f6 (Backdrop 2.0.1)
Copyright 2025 Kyant. Licensed under Apache 2.0; see LICENSE.

Backdrop commonMain and androidMain sources and LiquidBottomTabs, LiquidBottomTab,
DampedDragAnimation, InteractiveHighlight, DragGestureInspector are vendored here.
The rendering algorithms, AGSL shaders and spring specifications
are retained. Multiplatform expect/actual declarations are consolidated for Android.
PiliPlus adaptations pass its theme, distinguish user gestures from state updates,
and add selected-tab clicks without feedback loops. Frame waiting uses Compose's clock.
User tuning sets both navigation background passes to 4dp blur (upstream: 8dp).

AM-plus-plus interaction adaptation (local source reference):
Commit: 075f5f5dbd5a7e02957d6e65441ae8c6a171919c.
LiquidBottomTabs.FreeDragBridge/PillCentre and InteractiveHighlight press entry
points supply full-bar drag capture, current-cell holds and newest-held-pointer
handover. Its 56dp panel/48dp thumb heights are adopted. Horizontal length uses
40dp margins on each side by user request. Completion callbacks are adapted to
PiliPlus's single Flutter-owned selection and reselect actions; cancellation
restores selection without emitting an action. No Apple Music host hooks are used.
See AM-plus-plus-LICENSE for the reference project's GPL-3.0 license.
