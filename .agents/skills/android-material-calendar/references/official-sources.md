# Official sources for android-material-calendar

Summaries and links only. Do not paste whole pages into this file. Anything not checked against the page is listed under "To verify".

## Official rules (Google / Material)

| Topic | Source | Summary |
|---|---|---|
| Material Design 3 | https://m3.material.io | Design principles, colour roles, typography, shape, and motion guidance. Used as the reference for theme roles and layout intent. |
| BottomSheet (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/components/BottomSheet.md | Describes `BottomSheetBehavior` (states, peek height, drag and fling) and `BottomSheetDragHandleView`. The drag handle is part of the sheet and only works with the Behavior. |
| Color (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Color.md | Theme colour roles such as `colorSurface`, `colorSurfaceVariant`, and `colorSurfaceContainer*`. |
| Typography (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Typography.md | Text appearance tokens such as `textAppearanceTitleMedium`. |
| Shape (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Shape.md | Shape appearance and corner radius theming. |
| Motion (Material Components Android 1.12.0) | https://github.com/material-components/material-components-android/blob/1.12.0/docs/theming/Motion.md | Motion guidance for duration and easing. |
| Accessibility for apps | https://developer.android.com/guide/topics/ui/accessibility/apps | Touch targets, content descriptions, state descriptions, custom accessibility actions, and keyboard operation. |

## Project choices (not Material or Android mandates)

- Handle: 48dp minimum touch target, drag up to collapse, drag down to expand, snap by position at 0.5 and by fling velocity at 800 px/s.
- Automatic collapse when the body is below 560dp tall. The threshold is a project decision.
- Month swipe: horizontal dominance, a 64dp minimum distance, and a 24dp left/right edge exclusion so the navigation drawer edge keeps priority.
- Expand animation of 240 ms (80 ms minimum), skipped when the animator duration scale is 0.
- Toolbar: one month title and one 今天. No in-page header.
- Stored keys: `preferences_month_layout` (values `classic` / `agenda`), and the legacy `preferences_home_layout` (value `new` maps to 月历＋日程).

## Items to verify

- Whether `BottomSheetBehavior` could hold the schedule area without a custom linked layout. The current decision is not to use it (see `docs/design/month-view-contract.md`). This needs device confirmation before it is treated as final.
- Exact contrast of the handle and card colours on pure black (AMOLED) themes, measured on device.
- Whether the Material 1.12.0 pages still describe the same APIs in the Material version used by this project. Confirm against the version in `gradle/libs.versions.toml` before quoting.
- Whether the Skill is loaded automatically by the platform. Until confirmed, read the Skill and contract explicitly.
