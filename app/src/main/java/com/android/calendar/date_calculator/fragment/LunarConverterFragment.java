package com.android.calendar.date_calculator.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.lineageos.etar.R;
import com.android.calendar.date_calculator.util.DateCalculatorUtils;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LunarConverterFragment extends Fragment {

    private TextView gregorianDateText;
    private TextView lunarDateText;
    private EditText lunarYearInput, lunarMonthInput, lunarDayInput;
    private CheckBox leapMonthCheckbox;
    private Button convertToGregorianButton;
    private TextView gregorianResultText;

    private LocalDate selectedDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.lunar_converter_fragment, container, false);

        gregorianDateText = view.findViewById(R.id.gregorian_date_text);
        lunarDateText = view.findViewById(R.id.lunar_date_text);
        lunarYearInput = view.findViewById(R.id.lunar_year_input);
        lunarMonthInput = view.findViewById(R.id.lunar_month_input);
        lunarDayInput = view.findViewById(R.id.lunar_day_input);
        leapMonthCheckbox = view.findViewById(R.id.leap_month_checkbox);
        convertToGregorianButton = view.findViewById(R.id.convert_to_gregorian_button);
        gregorianResultText = view.findViewById(R.id.gregorian_result_text);

        selectedDate = LocalDate.now();
        updateGregorianDateText();
        convertAndDisplayLunarDate();

        gregorianDateText.setOnClickListener(v -> showDatePicker());
        convertToGregorianButton.setOnClickListener(v -> convertToGregorian());

        return view;
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_date)
                .setSelection(selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            selectedDate = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate();
            updateGregorianDateText();
            convertAndDisplayLunarDate();
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void updateGregorianDateText() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        gregorianDateText.setText(selectedDate.format(formatter));
    }

    private void convertAndDisplayLunarDate() {
        DateCalculatorUtils.LunarDate lunarDate = DateCalculatorUtils.convertToLunar(selectedDate);
        lunarDateText.setText(lunarDate.toString());
    }

    private void convertToGregorian() {
        String yearStr = lunarYearInput.getText().toString();
        String monthStr = lunarMonthInput.getText().toString();
        String dayStr = lunarDayInput.getText().toString();

        if (yearStr.isEmpty() || monthStr.isEmpty() || dayStr.isEmpty()) {
            Toast.makeText(getContext(), R.string.enter_lunar_date_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int year = Integer.parseInt(yearStr);
            int month = Integer.parseInt(monthStr);
            int day = Integer.parseInt(dayStr);
            boolean isLeap = leapMonthCheckbox.isChecked();

            // Convert Gregorian year to Chinese calendar cyclical year
            int cyclicalYear = year - 1900 + 36;

            LocalDate gregorianResult = DateCalculatorUtils.convertToGregorian(cyclicalYear, month - 1, day, isLeap);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            gregorianResultText.setText(gregorianResult.format(formatter));
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), R.string.invalid_number_toast, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.invalid_lunar_date_toast, Toast.LENGTH_SHORT).show();
        }
    }
}