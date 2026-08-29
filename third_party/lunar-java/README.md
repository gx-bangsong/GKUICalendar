# lunar-java (vendored, stripped)

Provenance: <https://github.com/6tail/lunar-java> at tag **v1.7.5**
(commit `cb93133`), MIT License — see `LICENSE` in this directory.

The Etar "contextual lunar" feature (`com.android.calendar.lunar`) needs only
Solar/Lunar date conversion, lunar festival lookup and solar-term (节气)
computation. The published Maven artifact (`cn.6tail:lunar:1.7.5`) could not be
used as a Gradle dependency because this repository's CI regenerates
`app/Android.bp` + `app/libs/` from the dependency graph
(`org.lineageos.generatebp`) and requires a byte-identical copy of every
non-AOSP artifact to be committed — which cannot be produced in a
network-restricted environment. Vendoring the (stripped) sources into the app
source tree instead keeps **both** build systems (Gradle and the AOSP
`Android.bp` glob `src/main/java/**/*.java`) working with zero build plumbing.

The sources therefore live at:

```
app/src/main/java/com/nlf/calendar/         (7 files)
app/src/main/java/com/nlf/calendar/util/    (3 files)
```

## Strip manifest (312 KB of 443 KB upstream)

Kept (unmodified except where noted):

| File | Notes |
| ---- | ----- |
| `Solar.java` | 3 methods removed (`nextMonth`, `next(int, boolean)`, `getSalaryRate`) + `HolidayUtil` import |
| `Lunar.java` | `eightChar` field + 14 methods removed (deprecated `getBaZi*` family, `getEightChar`, `getShuJiu`, `getFu`, `getFoto`, `getTao`) |
| `LunarMonth.java`, `LunarTime.java`, `LunarYear.java`, `NineStar.java`, `JieQi.java` | unmodified |
| `util/LunarUtil.java`, `util/SolarUtil.java`, `util/ShouXingUtil.java` | unmodified (lunar tables + solar-term astronomy) |

Dropped entirely (fortune-telling / holiday-legalization features not used by
Etar): `EightChar`, `eightchar/*` (八字/大运/流年), `Foto`, `FotoFestival`,
`util/FotoUtil` (佛历), `Tao`, `TaoFestival`, `util/TaoUtil` (道历), `Fu` (三伏),
`ShuJiu` (数九), `Holiday`, `util/HolidayUtil` (法定假日), `SolarHalfYear`,
`SolarMonth`, `SolarSeason`, `SolarWeek`, `SolarYear`.

Upstream unit tests were not vendored; equivalent coverage for the behaviour
Etar depends on lives in
`app/src/test/java/com/android/calendar/lunar/LunarHelperTest.kt`.
