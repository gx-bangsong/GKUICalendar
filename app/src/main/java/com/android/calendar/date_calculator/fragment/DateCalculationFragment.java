package com.android.calendar.date_calculator.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ws.xsoh.etar.R;
import com.android.calendar.date_calculator.util.DateCalculatorUtils;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class DateCalculationFragment extends Fragment {

    private TextView startDateText;
    private EditText daysInput;
    private RadioGroup directionGroup;
    private RadioButton forwardButton;
    private Button calculateButton;
    private TextView resultText;

    private LocalDate selectedStartDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.date_calculation_fragment, container, false);

        startDateText = view.findViewById(R.id.start_date_text);
        daysInput = view.findViewById(R.id.days_input);
        directionGroup = view.findViewById(R.id.direction_group);
        forwardButton = view.findViewById(R.id.forward_button);
        calculateButton = view.findViewById(R.id.calculate_button);
        resultText = view.findViewById(R.id.result_text);

        selectedStartDate = LocalDate.now();
        updateStartDateText();

        startDateText.setOnClickListener(v -> showDatePicker());

        calculateButton.setOnClickListener(v -> calculate());

        return view;
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_date)
                .setSelection(selectedStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            selectedStartDate = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate();
            updateStartDateText();
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void updateStartDateText() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        startDateText.setText(selectedStartDate.format(formatter));
    }

    private void calculate() {
        String daysString = daysInput.getText().toString();
        if (daysString.isEmpty()) {
            Toast.makeText(getContext(), R.string.enter_days_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        int days;
        try {
            days = Integer.parseInt(daysString);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), R.string.invalid_number_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        if (days < 0 || days > 10000) {
            Toast.makeText(getContext(), R.string.days_range_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isForward = forwardButton.isChecked();

        LocalDate resultDate = DateCalculatorUtils.calculateTargetDate(selectedStartDate, days, isForward);

        String dayOfWeek = resultDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        resultText.setText(getString(R.string.result_is, resultDate.format(formatter), dayOfWeek));
    }
}