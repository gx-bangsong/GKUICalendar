---
name: android-material-calendar
description: 为GKUICalendar的View/XML界面应用Android Material 3与Expressive设计原则，处理月视图、手柄、主题、动效、无障碍及自适应布局。
---

# android-material-calendar

Project skill for the GKUICalendar Android app (View and XML only, no Compose).

## Read first, every UI task

This platform does not guarantee that a skill is loaded automatically. Having this file in the
repository is not proof that it was used. At the start of every UI task, read explicitly:

1. `.agents/skills/android-material-calendar/SKILL.md` (this file)
2. `docs/design/month-view-contract.md` — the project constraints for navigation, layout, the handle, the Toolbar, and accessibility.
3. `.agents/skills/android-material-calendar/references/official-sources.md` — links and summaries of the official rules, plus items still to verify.
4. For settings sub-pages (`settings/`, the `preferences` XML and their dialogs): `docs/design/settings-md3-contract.md`, which separates official principles, project choices and unverified items.

State the paths you read in the task summary. Reading a file is not proof that the platform loaded this skill automatically.

## Current design target

- The current settings round targets standard Material 3 on Material Components 1.12.0. Material 3 Expressive is not an acceptance requirement for this round.
- The month view rules in `docs/design/month-view-contract.md` and the lifecycle rules still apply to every screen that uses them. Settings work does not change them.
- The lunar calendar subscreen is the sample. Other subscreens change only after the user accepts the sample.

## Hard rules

- View and XML only. No Compose, no new UI framework.
- Do not upgrade Material Components, do not raise targetSdk, do not add native libraries.
- Not upgrading Material and not adopting Expressive are limits of the current round's scope. They are not permanent prohibitions. A later round may change them with an explicit decision. The current settings round keeps Material Components 1.12.0 and uses standard Material 3 only.
- Month navigation has exactly one 月 entry (日、周、月、议程). The 月视图布局 setting chooses 经典月历 or 月历＋日程 inside MONTH. It is never a separate nav entry or a new home mode.
- Keep external values stable: DAY/WEEK/MONTH/AGENDA are not renumbered. Legacy HOME(6) maps to MONTH plus 月历＋日程.
- The Toolbar shows the browsing year-month and exactly one 今天. No in-page month header and no fixed caption.
- The handle never opens a modal or scrim, never covers the Toolbar, and never switches views.
- Store the user's expand choice and the automatic downgrade state separately. Recompute the automatic state on every layout pass.
- Tap on a date only selects it. Event creation goes through the FAB or an explicit button.
- Events come from `Event.loadEvents`/`EventLoader` on a background thread. Discard stale results.
- Subscription daily content (birthdays, shifts, rest days) appears as cards in the schedule area. It does not open the subscription management page.
- Classic view, mini-month clicks, notifications, widgets, and external Intents must keep their current behaviour.
- minSdk stays at its current value. Do not call APIs newer than minSdk without a version guard.

## Material guidance vs project choice

Material guidance is in `references/official-sources.md`. Project decisions (for example the
list minimum height, the edge exclusion, the swipe distance, and the degradation order) are
project choices, not Material mandates. They are recorded in `docs/design/month-view-contract.md`.

## Verification

- Compile status is established only by CI results. Do not report static reading or syntax checks as compile success.
- CI (`.github/workflows/build`) runs `assembleDebug`, which runs the unit tests in the packages listed in `app/build.gradle.kts`, then `generateBp` and the databinding check, and uploads the debug APK. It does not run lint or instrumented tests. A new test package must be added to that filter to run in CI.
- Android CLI, adb and device checks are reported separately, and only when they were actually run.
- Pure logic goes into JVM unit tests under the packages that CI runs (see `app/build.gradle.kts`).
- Device checks (phone and tablet, portrait and landscape, font 1.0/1.3/2.0, TalkBack, AMOLED, API 23–28) are reported separately from static and CI results.
