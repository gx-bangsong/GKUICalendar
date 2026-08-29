# Contextual lunar calendar (Smartisan-inspired)

Etar's month view normally shows only solar day numbers. This feature adds a
**contextual lunar layer**: most days stay clean, and the lunar calendar
reveals itself only when it matters — near the major Chinese festivals.

The interaction model is inspired by Smartisan OS: information appears when it
is relevant instead of permanently occupying every cell.

## The three states

| State | Where | What is drawn |
|---|---|---|
| **HIDDEN** | Outside every reveal window | Solar number only |
| **VISIBLE** | Inside a reveal window | Solar number + compact lunar text (11sp, `colorOnSurfaceVariant`) below it |
| **EMPHASIZED** | The festival day itself | `colorPrimaryContainer` chip (8dp radius) behind the solar number + festival name in 13sp bold `colorOnPrimaryContainer` |

Reveal windows: **春节 (Spring Festival)** opens a large window — 15 days
before, 7 days after. Every other enabled festival opens a smaller window —
5 days before, 3 days after. **除夕** (New Year's Eve) is the day before 春节
and shares its window. When the *24 solar terms* preference is on, a term day
(节气) is EMPHASIZED with the term's name, and only then.

The today pill keeps priority: on today's cell the pill is drawn instead of a
festival chip.

All colors resolve through Material 3 color roles at draw time, so day/night
theme switches need no cache invalidation.

## Settings

New *Lunar calendar* screen inside *General preferences*:

| Key | Type | Default |
|---|---|---|
| `pref_lunar_mode` | off / contextual / always | locale-qualified: **off** (English), **contextual** (zh, zh-rTW) |
| `pref_lunar_festivals` | multi-select of the 8 festivals | all enabled |
| `pref_lunar_show_jieqi` | switch | off |
| `pref_lunar_detail_always` | switch | off (full dates like 八月十五 instead of 十五) |

## Architecture

```
com.android.calendar.lunar
├── LunarHelper.kt      pure engine: Julian day → LunarInfo; the single
│                       entry point is getLunarInfo(...)
├── LunarWindow.kt      computes the reveal windows for the visible range,
│                       rebuilt lazily when the range or settings change
├── LunarCache.kt       small LRU for resolved LunarInfo
└── LunarDayRenderer.kt paints the VISIBLE/EMPHASIZED states (MD3 tokens)
```

`MonthWeekEventsView` resolves the lunar info for its week **once per bind**
(`precomputeLunarInfos()` in `setWeekParams`), never per draw. In OFF mode —
or outside every window — the array stays null and drawing skips the layer
entirely, so the feature is effectively free when unused.

## Data source

Festival and solar-term computation is [lunar-java](https://github.com/6tail/lunar-java)
v1.7.5 (MIT), vendored as stripped sources under
`app/src/main/java/com/nlf/calendar/` (~312 KB). See
`third_party/lunar-java/README.md` for provenance and why the Maven artifact
could not be used.

## Tests

`app/src/test/java/com/android/calendar/lunar/LunarHelperTest.kt` — 29 pure
JVM tests: Spring Festival dates pinned for 2020–2030, every reveal window
edge (F−15/F+7 and F−5/F+3 visible, one day beyond hidden), all 2024
festivals (腊八, 除夕, 元宵, 清明, 端午, 七夕, 中秋, 重阳), solar terms,
leap-month handling (2020 闰四月) and mode behavior.

CI executes them as part of every debug build: `app/build.gradle.kts` makes
`assembleDebug` depend on `testDebugUnitTest` when the `CI` env var is set
(this repository's workflows cannot be modified by the automation that
maintains this branch). The CI run is filtered to
`com.android.calendar.lunar.*` because the repository also carries a legacy
AOSP-era unit-test suite that was written for an instrumented runner and
fails on the plain JVM; it has never been part of CI and is untouched by
this feature.
