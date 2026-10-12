---
name: android-material-calendar
description: Use for GKUICalendar View/XML UI work on the month view, the 月历＋日程 schedule area, the drag handle, the Toolbar month title, day cells, subscription cards, Material 1.x theming, AMOLED colours, and TalkBack/large-font checks. Read docs/design/month-view-contract.md before editing these areas.
---

# android-material-calendar

Project skill for the GKUICalendar Android app (View and XML only, no Compose).

## Read first, every UI task

1. `docs/design/month-view-contract.md` — the project constraints for navigation, layout, the handle, the Toolbar, and accessibility.
2. `references/official-sources.md` — links and summaries of the official rules, plus items still to verify.

Report the paths you read in the task summary. If the platform does not load this skill automatically, read both files explicitly at the start of every UI task.

## Hard rules

- View and XML only. No Compose, no new UI framework.
- Do not upgrade Material Components, do not raise targetSdk, do not add Expressive, do not add native libraries.
- Month navigation has exactly one 月 entry (日、周、月、议程). The 月视图布局 setting chooses 经典月历 or 月历＋日程 inside MONTH. It is never a separate nav entry or a new home mode.
- Keep external values stable: DAY/WEEK/MONTH/AGENDA are not renumbered. Legacy HOME(6) maps to MONTH plus 月历＋日程.
- The Toolbar shows the browsing year-month and exactly one 今天. No in-page month header and no fixed caption.
- The handle never opens a modal or scrim, never covers the Toolbar, and never switches views.
- Store the user's expand choice and the automatic collapse state separately. Recompute the automatic state on every layout pass.
- Tap on a date only selects it. Event creation goes through the FAB or an explicit button.
- Events come from `Event.loadEvents`/`EventLoader` on a background thread. Discard stale results.
- Subscription daily content (birthdays, shifts, rest days) appears as cards in the schedule area. It does not open the subscription management page.
- Classic view, mini-month clicks, notifications, widgets, and external Intents must keep their current behaviour.

## Material guidance vs project choice

Material guidance is in `references/official-sources.md`. Project decisions (for example the 560dp automatic collapse threshold, the 24dp edge exclusion, and the 64dp swipe distance) are project choices, not Material mandates. Keep them in `docs/design/month-view-contract.md` and label them as such.

## Verification

- Compile status is established only by CI results. Do not report static reading or syntax checks as compile success.
- Pure logic goes into JVM unit tests (for example `PanelMath`, `MonthLayoutRules`).
- Device checks (phone and tablet, portrait and landscape, large font, TalkBack, AMOLED) are reported separately from static and CI results.
