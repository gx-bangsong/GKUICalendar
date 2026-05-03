package com.android.calendar.date_calculator;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import ws.xsoh.etar.R;
import com.android.calendar.date_calculator.fragment.DateCalculationFragment;
import com.android.calendar.date_calculator.fragment.DateIntervalFragment;
import com.android.calendar.date_calculator.fragment.LunarConverterFragment;
import com.android.calendar.theme.DynamicThemeKt;
import com.android.calendar.theme.ThemeUtils;
import com.android.calendar.theme.model.Theme;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class DateCalculatorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicThemeKt.applyThemeAndPrimaryColor(this);
        setContentView(R.layout.date_calculator_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Theme selectedTheme = ThemeUtils.INSTANCE.getTheme(this);
        boolean isPureBlackModeEnabled = ThemeUtils.INSTANCE.isPureBlackModeEnabled(this);
        boolean isDark = selectedTheme == Theme.DARK || (selectedTheme == Theme.SYSTEM && DynamicThemeKt.isSystemInDarkTheme(this));

        if (isDark) {
            AppBarLayout appBarLayout = findViewById(R.id.app_bar);
            int color = isPureBlackModeEnabled ? android.graphics.Color.BLACK : getResources().getColor(R.color.bg_dark, getTheme());
            appBarLayout.setBackgroundColor(color);
            toolbar.setBackgroundColor(color);
            if (getWindow() != null) {
                getWindow().setStatusBarColor(color);
            }
        }

        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new DateCalculationFragment();
                    case 1:
                        return new DateIntervalFragment();
                    case 2:
                        return new LunarConverterFragment();
                    default:
                        throw new IllegalStateException("Unexpected position: " + position);
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.date_calculation);
                    break;
                case 1:
                    tab.setText(R.string.date_interval);
                    break;
                case 2:
                    tab.setText(R.string.lunar_converter);
                    break;
            }
        }).attach();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
