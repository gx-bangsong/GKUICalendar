# Official sources for android-material-calendar

Summaries and links only. Do not paste whole pages into this file. Anything not checked
against the page is listed under "To verify".

## Official rules (Google / Material)

| Topic | Source | Summary |
|---|---|---|
| Material Design 3 | https://m3.material.io | Design principles, colour roles, typography, shape, and motion guidance. Used as the reference for theme roles and layout intent. |
| BottomSheet (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/components/BottomSheet.md | `BottomSheetBehavior` has collapsed and expanded states. Its peek height can be set dynamically, not only as a fixed value. `BottomSheetDragHandleView` is the handle view for a sheet that uses this Behavior. |
| Color (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Color.md | Theme colour roles such as `colorSurface`, `colorSurfaceVariant`, and `colorSurfaceContainer*`. |
| Typography (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Typography.md | Text appearance tokens such as `textAppearanceTitleMedium`. |
| Shape (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Shape.md | Shape appearance and corner radius theming. |
| Motion (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Motion.md | Motion guidance for duration and easing. |
| Accessibility for apps | https://developer.android.com/guide/topics/ui/accessibility/apps | Touch targets, content descriptions, state descriptions, custom accessibility actions, and keyboard operation. |

## Project choices (not Material or Android mandates)

- Handle: 48dp minimum touch target. Drag up collapses, drag down expands. Release snaps by position at 0.5, or by fling velocity at 800 px/s. Tap toggles.
- Custom handle instead of `BottomSheetBehavior`. This is a project decision. The Behavior supports dynamic peek height, so that is not the reason. The reasons are that its nested scroll and drag would compete with the month swipe and the list scroll, and the wide layout would need a different structure. Not adopted in this round.
- Expanded grid versus schedule list: the list keeps at least 120dp (experimental parameter, not a Material value). Lunar labels are hidden before the grid is auto-collapsed.
- Month swipe: horizontal dominance, a 64dp minimum distance, and a 24dp exclusion at each window edge so the navigation drawer edge keeps priority.
- Expand animation: 240 ms (80 ms minimum), skipped when the animator duration scale is 0.
- Toolbar: one month title and one 今天. No in-page header.
- Stored keys: `preferences_month_layout` (values `classic` / `agenda`). Legacy `preferences_home_layout` value `new` maps to 月历＋日程.

## Items to verify

- Whether a custom handle on a plain layout passes TalkBack and keyboard checks on device, and whether the handle moves smoothly on API 23–28.
- Whether the 120dp list minimum and the lunar-hide order are comfortable on a real phone at font 1.0, 1.3, and 2.0.
- Contrast of the handle and the card colours on pure black (AMOLED) themes, measured on device.
- Whether the Material 1.12.0 pages still describe the same APIs in the Material version this project uses. Confirm against `gradle/libs.versions.toml` before quoting.
