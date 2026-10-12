# Month view contract (GKUICalendar)

Status: project constraints for the 月历＋日程 work. These are project decisions. They are not claimed as Material mandates. Where a rule comes from official guidance, `.agents/skills/android-material-calendar/references/official-sources.md` says so.

## 1. Navigation

- Four views: 日、周、月、议程.
- Exactly one 月 entry. The separate 月历＋当天日程 entry is removed. Hiding its text or pointing it at the same page is not acceptable.
- DAY/WEEK/MONTH/AGENDA keep their existing values. Nothing is renumbered.

## 2. Month layout setting

- Settings: 月视图布局 → 经典月历 / 月历＋日程. Stored under `preferences_month_layout` (`classic` or `agenda`).
- 月历＋日程 is a layout inside MONTH. It is not a separate view or home mode.
- Layout choice and expand state are separate states. Expanding or collapsing never changes the layout value.
- Changing the layout while in 月 refreshes the layout and keeps the browse month, the selected date, and the list position.
- The default for new users is unchanged. The Chinese new-user default is a separate, later change.

## 3. Legacy HOME values

- Legacy HOME(6), in saved state and in the local `preferences_home_layout` value `new`, maps to MONTH plus 月历＋日程.
- A legacy value never falls back to week, never crashes, and never loses an explicit choice.
- The migration reads the old key and does not clear other preferences.
- Notifications, widgets, and external Intents are not rewritten. Their target events are kept.

## 4. Toolbar

- The Toolbar shows the browsing year-month. It has exactly one 今天.
- No fixed 月历＋当天日程 caption. No in-page month title, no prev/next buttons, and no today row inside the page.
- 今天 updates the browse month, the selected date, the day list, and the FAB date together.

## 5. Month swipe

- Left and right swipes on the month grid change the month.
- A swipe needs horizontal dominance over vertical movement, a touch slop, and at least 64dp of travel (project choice).
- Touches within 24dp of either edge of the window are ignored, so the drawer edge gesture keeps working (project choice). The edges are the window's, not the screen's, so split-screen is handled.
- A second pointer cancels the swipe.
- Cell taps still select the date.

## 6. Schedule area and handle

- The selected-date title sits in the schedule area and names the day the list belongs to.
- The handle is a small rounded bar at the top centre of the schedule area. Its touch target is at least 48dp.
- Drag up collapses the grid to the selected week. Drag down expands it. On release the state snaps by position (halfway) or by fling velocity (800 px/s).
- A tap on the handle toggles the state. No long-press is required.
- A press during an animation takes over at the current visual position. An animation that was cancelled cannot jump to its old target.
- No modal, no scrim, no covering of the Toolbar, and no jump to agenda, week, or day.
- The collapsed state keeps the handle and the selected-date title usable. It is the 周历＋当天日程 state: the selected week and the day list (project choice, not a new view or nav entry).
- While collapsed, a left or right swipe, and the TalkBack previous and next actions, move the selection by one week on the same weekday. The step uses civil days, so month, year and leap-day boundaries are handled. The browse month follows the selected day, so the collapsed grid always shows the selected week, and expanding shows the month of the selected day.
- While expanded, the same swipe and actions move by month. A day tap in the collapsed week only updates the list, the FAB date and the selection.
- The Toolbar keeps one month title and one 今天 in both states.
- Vertical list scrolling never collapses the month.
- Touch handling uses touch slop, handles pointer cancel and multi-touch, and converts coordinates without `MotionEvent.getRawX(int)`, which needs API 29 (minSdk is 23).
- Keyboard: DPAD up collapses, DPAD down expands, and Enter or Space toggles.
- Accessibility: the handle has a state description (English "Month shown" / "Month hidden"; Chinese locales use their own translations), a named toggle action, and a click equivalent. The grid has previous-month and next-month accessibility actions.

### Why the handle is a custom view (round 2 evaluation)

- `BottomSheetBehavior` supports dynamic peek heights. The collapsed height could therefore be "one week row plus the title". That is not the reason it is not used.
- The reasons are structural. The Behavior owns the drag and nested scroll of the whole sheet. That would compete with the month swipe on the grid and with the day list's own scroll. The wide (tablet and landscape) layout places the grid and list side by side, which the sheet does not model.
- `BottomSheetDragHandleView` is designed for the Behavior and is not used.

This is a project decision made in this round. It has not been checked on device. Revisit it if device results show the custom handle fails TalkBack, keyboard, or smoothness checks.

## 7. Automatic collapse (measured, not a fixed threshold)

- The automatic state is a space downgrade. It is not a user choice and it is not a Material rule.
- The check uses the real numbers for this window: the measured weekday row, the 48dp handle, the measured day title, the measured row height at the current font scale, and six rows. The schedule list keeps at least 120dp (experimental parameter).
- If the expanded grid does not fit, lunar labels are hidden first, because they are secondary text. If it still does not fit, the grid is auto-collapsed.
- The user's choice and the automatic state are stored separately. An explicit "expanded" stays stored during a downgrade and applies again once there is room. An explicit "collapsed" always holds.
- The automatic state is recomputed on every layout pass (rotation, split-screen, size change). An expired downgrade is never reused.
- A normal window with room for six rows and the list minimum is not collapsed.

## 8. Phone and tablet

- Phone and tablet, portrait and landscape, share the same semantics.
- Wide layout (at least 600dp wide and wider than tall) places the grid and list side by side. It hides the handle. It does not copy the bottom sheet, and it adds no third navigation view.

## 9. Cells, markers, and cards

- Each day cell has three slots from top to bottom: the day number (a square of at least 28dp, larger at large font), the lunar label (only when shown), and a dot row (5dp, after a 2dp margin).
- Lunar text follows `pref_lunar_mode` per day, using the same rule as the classic month view (`LunarHelper.getLunarInfo`). OFF shows nothing. CONTEXTUAL (臨近節日) shows text only inside a festival window. ALWAYS shows text on every day. A festival or solar-term day is bold in the primary colour. Both enabled modes reserve the lunar slot, so the row height does not change with the day content. A day with no text has an empty label, never old text. A change of any lunar setting drops the old labels at once and reloads the month.
- Every slot is sized from the same formulas that the grid uses for the row height. Slots never overlap. The day number and the dots do not shrink.
- Large font grows the row, up to the room the layout allows. When the room is not enough, the lunar slot is hidden before the grid is collapsed. The schedule list is never squeezed below its minimum.
- The lunar label may shrink its width (one line) to fit a cell. That is a width fallback, not evidence of large-font support.
- Multiple events show as dots in the dot row, up to three.
- Enabled subscription daily content (birthdays, work shifts, rest days) appears as cards in the schedule area. Every matching birthday and rest day is shown. The cards do not open the subscription management page.
- When the user returns from another screen (settings, subscriptions, birthdays, shifts), the lunar labels, the month markers, and the day list are reloaded. The month, the selection, and the list position are kept.

## 10. Lifecycle

- The loader is created with the view and shut down when the view is destroyed. Results from a destroyed view are discarded.
- A new view reloads normally.
- View references are cleared when the view is destroyed. Touch and layout listeners and animations are removed.

## 11. Colour and AMOLED

- Use theme roles only (`colorSurface`, `colorSurfaceVariant`, `colorOnSurface`, and similar).
- Pure-black themes set `colorSurfaceContainer` to the background. Cards therefore use `colorSurfaceVariant` so they keep a visible level above the background.
- The black background (`colorBackgroundBlack`) applies to the Black theme, to Dark with pure black on, and to System with a dark system and pure black on. It also sets the window background. The rule is in `ThemeRules`.
- Theme changes apply on return. The month screen keeps the effective theme key from its creation. When the key differs in `onResume` (for example the pure black switch changed in settings), the screen is recreated once. The month state (browse month, selection, expand state, list position) is kept.
- The background stays consistent across the grid, the schedule area, and the list.

## 12. Round scope limits (not permanent)

- Not in this round: Material upgrade, Expressive, Compose, native libraries, targetSdk changes, holiday data, todo data, and the legacy dialog refactor.
- Classic view behaviour, mini-month clicks, and external Intents must not change.
