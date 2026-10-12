# Month view contract (GKUICalendar)

Status: project constraints for the 月历＋日程 work. These are project decisions. They are not claimed as Material mandates. Where a rule comes from official guidance, `references/official-sources.md` says so.

## 1. Navigation

- Four views: 日、周、月、议程.
- Exactly one 月 entry. The separate 月历＋当天日程 entry is removed. Hiding its text or pointing it at the same page is not acceptable.
- DAY/WEEK/MONTH/AGENDA keep their existing values. Nothing is renumbered.

## 2. Month layout setting

- Settings: 月视图布局 → 经典月历 / 月历＋日程. Stored under `preferences_month_layout` (`classic` or `agenda`).
- 月历＋日程 is a layout inside MONTH. It is not a separate view or home mode.
- Layout choice and expand state are separate states. Expanding or collapsing never changes the layout value.
- Changing the layout while in 月 refreshes the layout and keeps the browse month, the selected date, and the list position.
- The default for new users is unchanged in this round. The Chinese new-user default is a later, separate change.

## 3. Legacy HOME values

- Legacy HOME(6), in saved state and in the local `preferences_home_layout` value `new`, maps to MONTH plus 月历＋日程.
- A legacy value must never fall back to week, crash, or lose an explicit choice.
- The migration reads the old key and does not clear other preferences.
- Notifications, widgets, and external Intents are not rewritten. Their target events are kept.

## 4. Toolbar

- The Toolbar shows the browsing year-month. It has exactly one 今天.
- No fixed 月历＋当天日程 caption. No in-page month title, no prev/next buttons, and no today row inside the page.
- 今天 updates the browse month, the selected date, the day list, and the FAB date together.

## 5. Month swipe

- Left and right swipes on the month grid change the month.
- A swipe needs horizontal dominance over vertical movement and a touch slop. It also needs at least 64dp of travel (project choice).
- Touches within 24dp of either screen edge are ignored so the drawer edge gesture keeps working (project choice).
- A second pointer cancels the swipe.
- Cell taps still select the date.

## 6. Schedule area and handle

- The selected-date title sits in the schedule area and names the day the list belongs to.
- The handle is a small rounded bar at the top centre of the schedule area. Its touch target is at least 48dp.
- Drag up collapses the grid to the selected week. Drag down expands it. On release the state snaps by position (halfway) or by fling velocity (800 px/s).
- A tap on the handle toggles the state.
- No long-press is required.
- No modal, no scrim, no covering of the Toolbar, and no jump to agenda, week, or day.
- The collapsed state keeps the handle and the selected-date title usable.
- Vertical list scrolling never collapses the month.
- Touch handling uses touch slop, and it handles pointer cancel and multi-touch.
- Keyboard: DPAD up collapses, DPAD down expands, and Enter or Space toggles.
- Accessibility: the handle has a state description ("展开" / "收起"), a named toggle action, and a click equivalent. The grid has previous-month and next-month accessibility actions.

### Why BottomSheetBehavior is not used (round 2 evaluation)

- `BottomSheetBehavior` treats the whole sheet as one child of a CoordinatorLayout. Its collapsed height is a fixed peek height in pixels. This project's collapsed height is "one week row plus the title", which changes with row count, font scale, and the selected week.
- The Behavior owns nested scrolling and drag for the whole sheet. That would compete with the month swipe handled by the grid and with the day list's own scroll.
- `BottomSheetDragHandleView` is tied to the sheet. It does not work without the Behavior.
- The wide (tablet and landscape) layout places the grid and list side by side. A bottom sheet would copy the phone shape there, which the product does not want.

So the handle is a custom View linked to a plain layout, with its drag math in `PanelMath` (pure Kotlin, unit tested). This is a project decision. It needs device confirmation.

## 7. Automatic collapse

- On a short body (below 560dp, project choice) the grid is auto-collapsed.
- The user's expand choice is stored separately and is never overwritten by the automatic state.
- The automatic state is recomputed on every layout pass (rotation, split screen, font change). An expired auto-collapse is never reused.

## 8. Phone and tablet

- Phone and tablet, portrait and landscape, share the same semantics.
- Wide layout (at least 600dp wide and wider than tall) places the grid and list side by side. It hides the handle. It does not copy the bottom sheet, and it adds no third navigation view.

## 9. Cells, markers, and cards

- Each day cell has three fixed slots: the day number, the lunar label (when lunar display is on), and a dot row. A lunar label never covers the day number, and dots never cover the lunar label.
- Text uses auto-size. Row height never drops below the font-scaled minimum, so large text grows the rows instead of clipping.
- Multiple events show as dots in the dot row.
- Enabled subscription daily content (birthdays, work shifts, rest days) appears as cards in the schedule area. Every matching birthday and rest day is shown. The cards do not open the subscription management page.

## 10. Colour and AMOLED

- Use theme roles only (`colorSurface`, `colorSurfaceVariant`, `colorOnSurface`, and similar).
- Pure-black themes set `colorSurfaceContainer` to the background. Cards therefore use `colorSurfaceVariant` so they keep a visible level above the background.
- The background stays consistent across the grid, the schedule area, and the list.

## 11. Out of scope for this contract

- Todo data, statutory holidays, and the legacy dialog refactor.
- Classic view behaviour, mini-month clicks, and external Intents. They must not change.
- Compose, Material upgrades, Expressive, and native libraries.
