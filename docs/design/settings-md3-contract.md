# Settings sub-pages: Material 3 contract (lunar sample)

Status: project constraints for the settings sub-page round. This round targets standard Material 3 on Material Components 1.12.0. It does not target Material 3 Expressive. The lunar calendar page is the sample. Round 7 promotes the same look to the General (通用设置) and Display details (显示详情) pages. Other sub-pages keep their current look.

The three parts below are kept apart on purpose: official principles, project choices, and what is not verified yet.

## 1. Official principles

Source: Android settings design guide, `https://developer.android.com/design/ui/mobile/guides/patterns/settings` (read 2026-10-20). Component details follow the Material Components 1.12.0 documentation, `https://github.com/material-components/material-components-android/tree/1.12.0/docs`.

- Group related settings. Use headings or visual containment between groups rather than a box around each item.
- A settings section uses a list. Each item has a primary label, optional supporting text for the current status, and a selection control.
- An on/off setting uses a switch or a checkbox. Radio buttons are not used for a boolean setting or for fewer than two choices.
- A set of discrete options from which the user picks one is presented as radio buttons in a dialog or a child screen.
- The label of the item that opens a subscreen matches the subscreen title.
- Keep the number of visible settings manageable. Put deeper groups on a subscreen.
- Large screens use a list-detail layout. This round does not implement it. See section 2.

## 2. Project choices

Scope and mechanism:

- Scope: the lunar calendar subscreen (sample), the General page and the Display details page. The time zone picker, quick responses, calendar accounts and subscriptions, event editing, system pickers and the other subscreens are not restyled.
- Styling is applied through row layouts (`app:layout`, `app:widgetLayout`) that `lunar_preferences.xml`, both `general_preferences.xml` variants and `view_details_preferences.xml` reference. The app theme is not changed.
- `preferenceTheme` is not changed. `PreferenceFragmentCompat` applies it to the whole `SettingsActivity` theme, so a change there would restyle every subscreen.
- No new dependency. Material Components stays at 1.12.0. No Expressive theme.

Groups and rows:

- Groups: 显示 (display: 显示农历, 显示完整农历日期) and 节日与节气 (festivals and solar terms: 触发农历显示的节日, 显示二十四节气). The order within the groups changes. No preference key, default value or meaning changes.
- The group headings use `colorOnSurfaceVariant`, not `colorPrimary`. The light theme's `colorPrimary` (#41c3b1) has a contrast of about 2:1 on the light background, which is too low for text. Hierarchy comes from the label style and the spacing.
- Row minimum height is 56dp. Touch targets stay at or above 48dp. Title uses `textAppearanceBodyLarge` with `colorOnSurface`. Supporting text uses `textAppearanceBodyMedium` with `colorOnSurfaceVariant`. The heights are not fixed, so text wraps and is not clipped.
- Switches are `MaterialSwitch` with `@+id/switchWidget`, `focusable=false` and `clickable=false`. The row handles the single toggle, so a tap changes the value once.
- Binding pairing (androidx.preference 1.2.1): the Preference class decides the switch id. `SwitchPreferenceCompat` binds `R.id.switchWidget`. `SwitchPreference` binds `android.R.id.switch_widget` and only handles `android.widget.Switch`. A `MaterialSwitch` layout therefore goes only with `SwitchPreferenceCompat`. The lunar rows use it. Mixing the two leaves the switch unbound.
- Source for 1.2.1: the androidx repo has no 1.2.1 tag. Files were taken at the last commit before the 2023-07-26 release date, with no later change to those files on main before that date. Release-note commit SHAs are not in the GitHub mirror.
- The title of the subscreen is 农历, the same as the entry in the general page.

Selection and dialogs:

- 显示农历 (one of off, near festivals only, every day) is a single choice. It is shown in the MD3 choice dialog (`SettingsChoiceDialogFragment`, `MaterialAlertDialogBuilder` with the activity context). Tapping an entry closes the dialog and saves at once.
- 触发农历显示的节日 is a multiple choice. The MD3 dialog saves on OK and discards on Cancel. The summary follows the saved value in entry order: 全部节日 when all are selected, 未选择节日 when none is selected, and the joined names otherwise. Joiner: "、" in Chinese, ", " in English.
- Round 7: the same MD3 choice dialog serves the ListPreference and MultiSelectListPreference rows of the General and Display details pages. It routes by preference type, not by key. The title falls back to the row title when the preference has no dialog title. Other DialogPreference types, the system pickers and the time zone picker keep the framework path.

Layout width and return:

- When the window is wider than 600dp, the list content is centred in a 600dp column. Narrow windows use the full width.
- The parent general page keeps its list scroll position when the user returns from a subscreen. The position is saved and restored by that page. This applies to every subscreen that opens from it.

## 3. Verification status

Lunar sample (round 6, `c7fe6a7`): accepted by the user on a device for portrait and landscape scroll alignment, dialog appearance and centring, rotation, cancel and confirm, switch, persistence, summary, theme readability and back position. This covers the lunar page only.

General and Display details (round 7): the items below are not verified. CI and static guards check the XML and the source text only. They do not check alignment, dialogs, rotation or TalkBack.

Not verified yet (round 7 pages):

- Visual result on phone and tablet, portrait and landscape, narrow split-screen, and font scales 1.0, 1.3 and 2.0. No screenshots were produced.
- Contrast of the group headings, supporting text and switches in the light, normal dark, dynamic colour and AMOLED themes. Only the theme roles are used. No measurement was made.
- TalkBack order and wording, the switch state announcement, and dialog focus.
- A single toggle per tap, and rotation while a dialog is open. The framework behaviour and the attribute contract were read from the androidx source. Neither was run on a device.
- Scroll restore on return. It is not device-tested.
- Lint. CI does not run lint, and lint was not run locally.
- Soong (Android.bp) build. The `build` composite action runs `generateBp` and fails if `git status` shows a change. That proves the generated file is in sync. It does not prove a Soong build.
- Android CLI screenshot, layout and input checks. The CLI was not available in this environment.
