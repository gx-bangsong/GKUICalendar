package com.android.calendar.date_calculator.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

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

public class DateIntervalFragment extends Fragment {

    private TextView startDateText;
    private TextView endDateText;
    private Button calculateButton;
    private TextView resultText;

    private LocalDate selectedStartDate;
    private LocalDate selectedEndDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.date_interval_fragment, container, false);

        startDateText = view.findViewById(R.id.start_date_text);
        endDateText = view.findViewById(R.id.end_date_text);
        calculateButton = view.findViewById(R.id.calculate_button);
        resultText = view.findViewById(R.id.result_text);

        selectedStartDate = LocalDate.now();
        selectedEndDate = LocalDate.now();
        updateStartDateText();
        updateEndDateText();

        startDateText.setOnClickListener(v -> showDatePicker(true));
        endDateText.setOnClickListener(v -> showDatePicker(false));

        calculateButton.setOnClickListener(v -> calculate());

        return view;
    }

    private void showDatePicker(boolean isStartDate) {
        LocalDate initialDate = isStartDate ? selectedStartDate : selectedEndDate;

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_date)
                .setSelection(initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (isStartDate) {
                selectedStartDate = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate();
                updateStartDateText();
            } else {
                selectedEndDate = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate();
                updateEndDateText();
            }
        });

        datePicker.show(getParentFragmentManager(), "DATE_PICKER");
    }

    private void updateStartDateText() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        startDateText.setText(selectedStartDate.format(formatter));
    }

    private void updateEndDateText() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        endDateText.setText(selectedEndDate.format(formatter));
    }

    private void calculate() {
        long difference = DateCalculatorUtils.calculateDateDifference(selectedStartDate, selectedEndDate);
        resultText.setText(getString(R.string.interval_result, difference));
    }
}