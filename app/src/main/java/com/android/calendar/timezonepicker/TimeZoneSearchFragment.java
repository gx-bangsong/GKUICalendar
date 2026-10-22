/*
 * Copyright (C) 2026 The GKUICalendar Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar.timezonepicker;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ws.xsoh.etar.R;

/**
 * Full-screen time zone search page for the event form. It is a Fragment placed over the whole
 * activity content, not a dialog, and it is not an immersive screen: the system bars stay.
 *
 * <p>The page is shared by the event form and the settings page. Each caller passes its own
 * request key, the current zone ID and the reference time for the offset text as arguments.
 * Selecting a zone publishes its real zone ID under that request key on the parent
 * FragmentManager and removes the page. Back and the navigation arrow remove the page without a
 * result, so the original zone is kept. The page never saves anything itself.
 */
public class TimeZoneSearchFragment extends Fragment {

    public static final String TAG = "timeZoneSearchPage";

    /** Bundle key that holds the selected zone ID (for example "Asia/Shanghai"). */
    public static final String RESULT_TIME_ZONE_ID = "time_zone_id";

    private static final String ARG_REQUEST_KEY = "request_key";
    private static final String ARG_CURRENT_ZONE_ID = "current_zone_id";
    private static final String ARG_REFERENCE_MILLIS = "reference_millis";
    private static final String STATE_HAS_FOCUS = "has_focus";
    private static final String STATE_FIRST_POSITION = "first_position";
    private static final String STATE_FIRST_OFFSET = "first_offset";
    private static final String STATE_QUERY = "query";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ZONE = 1;
    private static final int TYPE_MESSAGE = 2;

    private String mRequestKey;
    private String mCurrentZoneId;
    private long mReferenceMillis;

    /** Loaded once per fragment instance. Typing only filters this list, never reloads it. */
    private final List<TimeZoneSearchMatcher.Entry> mEntries = new ArrayList<>();
    private TimeZoneSearchMatcher.Entry mCurrentEntry;
    private boolean mLoaded;
    private boolean mLoadRequested;
    private boolean mResultDelivered;
    private String mReferenceDateText = "";

    private EditText mSearchInput;
    private ImageButton mClearButton;
    /** The query as last seen by the watcher or restored; saved even while the view is gone. */
    private String mLastQuery = "";
    private RecyclerView mResults;
    private ResultAdapter mAdapter;
    private int mPendingFirstPosition = -1;
    private int mPendingFirstOffset;

    private ExecutorService mLoader;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /** Creates a page. Only plain values go into the arguments, never a View or a callback. */
    public static TimeZoneSearchFragment newInstance(String requestKey, String currentZoneId,
            long referenceMillis) {
        TimeZoneSearchFragment page = new TimeZoneSearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_KEY, requestKey);
        args.putString(ARG_CURRENT_ZONE_ID, currentZoneId);
        args.putLong(ARG_REFERENCE_MILLIS, referenceMillis);
        page.setArguments(args);
        return page;
    }

    /**
     * Opens one page on the given FragmentManager, which must be the activity's manager so the
     * result reaches the caller. It does nothing and returns false when a page is already open
     * or the state is already saved, so pages never stack and no commit is lost silently.
     */
    public static boolean open(FragmentManager fm, String requestKey, String currentZoneId,
            long referenceMillis) {
        if (fm == null || fm.isStateSaved() || fm.findFragmentByTag(TAG) != null) {
            return false;
        }
        fm.beginTransaction()
                .add(android.R.id.content,
                        newInstance(requestKey, currentZoneId, referenceMillis), TAG)
                .commit();
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        mRequestKey = args != null ? args.getString(ARG_REQUEST_KEY) : null;
        mCurrentZoneId = args != null ? args.getString(ARG_CURRENT_ZONE_ID) : null;
        mReferenceMillis = args != null
                ? args.getLong(ARG_REFERENCE_MILLIS) : System.currentTimeMillis();
        mLoader = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_timezone_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();

        view.findViewById(R.id.timezone_search_back).setOnClickListener(
                v -> closeWithoutSelection());

        mSearchInput = view.findViewById(R.id.timezone_search_input);
        mClearButton = view.findViewById(R.id.timezone_search_clear);
        // Clear empties the query only. The page, focus and keyboard stay as they are.
        mClearButton.setOnClickListener(v -> mSearchInput.setText(""));

        // Restore the query before the watcher exists. The restore must not run a filter pass
        // against a list that is not loaded yet, and it must not be overwritten by the empty
        // initial query. The rows are rebuilt once the list is available.
        if (savedInstanceState != null) {
            mLastQuery = savedInstanceState.getString(STATE_QUERY, "");
            mSearchInput.setText(mLastQuery);
            mSearchInput.setSelection(mSearchInput.length());
            mPendingFirstPosition = savedInstanceState.getInt(STATE_FIRST_POSITION, -1);
            mPendingFirstOffset = savedInstanceState.getInt(STATE_FIRST_OFFSET, 0);
            if (savedInstanceState.getBoolean(STATE_HAS_FOCUS)) {
                mSearchInput.requestFocus();
            }
        } else {
            // Focus opens the keyboard only on first show, not after a rotation.
            mSearchInput.requestFocus();
        }
        updateClearButton();

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mLastQuery = s.toString();
                updateClearButton();
                refreshRows();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        // Search on the keyboard only hides the keyboard. It never commits a result, so a
        // stray Enter cannot pick the first row.
        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            return true;
        });

        mResults = view.findViewById(R.id.timezone_search_results);
        mResults.setLayoutManager(new LinearLayoutManager(context));
        mAdapter = new ResultAdapter();
        mResults.setAdapter(mAdapter);

        // The page pads itself. Left, top and right use the larger of the system bars and the
        // display cutout. Bottom uses the larger of the navigation bar and the IME, never their
        // sum, so no empty band is left when the keyboard is hidden. Window insets are already
        // intersected with the window, so an adjustResize window does not count the IME twice.
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = Insets.max(insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                    insets.getInsets(WindowInsetsCompat.Type.displayCutout()));
            int bottom = Math.max(
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            v.setPadding(bars.left, bars.top, bars.right, bottom);
            return insets;
        });

        mReferenceDateText = DateUtils.formatDateTime(context, mReferenceMillis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                        | DateUtils.FORMAT_ABBREV_MONTH);

        // The only back handler of this page. It is added to the view lifecycle, so it exists
        // only while this view is alive, and it is the newest callback, so it runs before the
        // host screen's handler. While it is enabled, back never reaches the host Activity.
        OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackRequested();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                backCallback);

        if (mLoaded) {
            refreshRows();
        } else if (TimeZoneSearchRouting.shouldStartLoad(mLoaded, mLoadRequested)) {
            loadEntries(context.getApplicationContext());
        }
    }

    /** One back rule: with the keyboard visible, hide it and stay; otherwise leave the page. */
    private void onBackRequested() {
        if (TimeZoneSearchRouting.onBack(isImeVisible())
                == TimeZoneSearchRouting.BackAction.HIDE_KEYBOARD) {
            hideKeyboard();
            return;
        }
        closeWithoutSelection();
    }

    private boolean isImeVisible() {
        if (mSearchInput == null) {
            return false;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mSearchInput);
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
    }

    private void updateClearButton() {
        if (mClearButton != null && mSearchInput != null) {
            mClearButton.setVisibility(mSearchInput.length() > 0 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HAS_FOCUS, mSearchInput != null && mSearchInput.hasFocus());
        outState.putString(STATE_QUERY, mLastQuery);
        if (mResults != null && mResults.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) mResults.getLayoutManager();
            int position = layoutManager.findFirstVisibleItemPosition();
            View first = position == RecyclerView.NO_POSITION
                    ? null : layoutManager.findViewByPosition(position);
            outState.putInt(STATE_FIRST_POSITION,
                    position == RecyclerView.NO_POSITION ? 0 : position);
            outState.putInt(STATE_FIRST_OFFSET,
                    first == null ? 0 : first.getTop() - mResults.getPaddingTop());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // A queued load result is kept on purpose: it only fills the per-instance zone list, and
        // the next view reads that list. It never touches a view that is already gone.
        mSearchInput = null;
        mClearButton = null;
        mResults = null;
        mAdapter = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoader != null) {
            mLoader.shutdownNow();
        }
    }

    /** Reads the zone list once, off the main thread, and then keeps it for this instance. */
    private void loadEntries(Context appContext) {
        final String currentZoneId = mCurrentZoneId;
        final long referenceMillis = mReferenceMillis;
        mLoadRequested = true;
        mLoader.execute(() -> {
            TimeZoneData data = new TimeZoneData(appContext, currentZoneId, referenceMillis);
            List<TimeZoneSearchMatcher.Entry> loaded = new ArrayList<>(data.size());
            for (int i = 0; i < data.size(); i++) {
                TimeZoneInfo tzi = data.get(i);
                if (tzi == null || TextUtils.isEmpty(tzi.mTzId)) {
                    continue;
                }
                loaded.add(new TimeZoneSearchMatcher.Entry(tzi.mTzId, tzi.mDisplayName,
                        tzi.mCountry,
                        TimeZoneSearchMatcher.offsetAt(tzi.mTzId, referenceMillis)));
            }
            mMainHandler.post(() -> onEntriesLoaded(loaded));
        });
    }

    private void onEntriesLoaded(List<TimeZoneSearchMatcher.Entry> loaded) {
        if (!isAdded()) {
            return;
        }
        mEntries.clear();
        mEntries.addAll(loaded);
        mCurrentEntry = null;
        for (TimeZoneSearchMatcher.Entry entry : mEntries) {
            if (entry.id.equals(mCurrentZoneId)) {
                mCurrentEntry = entry;
                break;
            }
        }
        mLoaded = true;
        refreshRows();
    }

    /** Rebuilds the visible rows from the current query. Filtering runs on the loaded list. */
    private void refreshRows() {
        if (mAdapter == null) {
            return;
        }
        String query = mSearchInput == null ? "" : mSearchInput.getText().toString();
        List<Row> rows = buildRows(query);
        mAdapter.setRows(rows);
        // A restored position waits for the loaded list. It is consumed once, here, after the
        // list is loaded; an unloaded (empty) list never consumes it.
        if (mPendingFirstPosition >= 0 && mLoaded && mResults != null) {
            final int position = mPendingFirstPosition;
            final int offset = mPendingFirstOffset;
            mPendingFirstPosition = -1;
            if (TimeZoneSearchRouting.canApplyPendingPosition(mLoaded, rows.size(), position)) {
                mResults.post(() -> {
                    if (mResults != null && mResults.getLayoutManager() != null) {
                        ((LinearLayoutManager) mResults.getLayoutManager())
                                .scrollToPositionWithOffset(position, offset);
                    }
                });
            }
        }
    }

    private List<Row> buildRows(String query) {
        List<Row> rows = new ArrayList<>();
        if (!mLoaded) {
            return rows;
        }
        if (TimeZoneSearchMatcher.normalize(query).isEmpty()) {
            rows.add(Row.header(getString(R.string.timezone_search_current)));
            if (mCurrentEntry != null) {
                rows.add(Row.zone(mCurrentEntry));
            } else {
                // Never show GMT for a zone we do not know; show the ID as it is stored.
                rows.add(Row.message(getString(R.string.timezone_search_unknown)
                        + (mCurrentZoneId == null ? "" : ": " + mCurrentZoneId)));
            }
            rows.add(Row.header(getString(R.string.timezone_search_candidates)));
            for (TimeZoneSearchMatcher.Entry entry : mEntries) {
                rows.add(Row.zone(entry));
            }
        } else {
            List<TimeZoneSearchMatcher.Entry> matches =
                    TimeZoneSearchMatcher.filter(mEntries, query);
            if (matches.isEmpty()) {
                rows.add(Row.message(getString(R.string.timezone_search_no_results,
                        query.trim())));
            } else {
                for (TimeZoneSearchMatcher.Entry entry : matches) {
                    rows.add(Row.zone(entry));
                }
            }
        }
        return rows;
    }

    /** Publishes the real zone ID once, then removes this page. The event is not saved here. */
    private void selectTimeZone(String zoneId) {
        if (mRequestKey == null
                || !TimeZoneSearchRouting.canDeliverResult(mResultDelivered, isAdded())) {
            return;
        }
        mResultDelivered = true;
        hideKeyboard();
        Bundle result = new Bundle();
        result.putString(RESULT_TIME_ZONE_ID, zoneId);
        FragmentManager fm = getParentFragmentManager();
        fm.setFragmentResult(mRequestKey, result);
        fm.beginTransaction().remove(this).commit();
    }

    /** Back or the arrow: removes the page without a result, so the zone is unchanged. */
    private void closeWithoutSelection() {
        if (!TimeZoneSearchRouting.canDeliverResult(mResultDelivered, isAdded())) {
            return;
        }
        mResultDelivered = true;
        hideKeyboard();
        getParentFragmentManager().beginTransaction().remove(this).commit();
    }

    private void hideKeyboard() {
        if (mSearchInput == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    /** One visible row: a section header, a message, or a selectable zone. */
    private static final class Row {
        final int type;
        final String text;
        final TimeZoneSearchMatcher.Entry entry;

        private Row(int type, String text, TimeZoneSearchMatcher.Entry entry) {
            this.type = type;
            this.text = text;
            this.entry = entry;
        }

        static Row header(String text) {
            return new Row(TYPE_HEADER, text, null);
        }

        static Row message(String text) {
            return new Row(TYPE_MESSAGE, text, null);
        }

        static Row zone(TimeZoneSearchMatcher.Entry entry) {
            return new Row(TYPE_ZONE, null, entry);
        }
    }

    private static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView text;

        TextHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.timezone_row_text);
        }
    }

    private static final class ZoneHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;
        final TextView offset;

        ZoneHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.timezone_row_name);
            detail = itemView.findViewById(R.id.timezone_row_detail);
            offset = itemView.findViewById(R.id.timezone_row_offset);
        }
    }

    private final class ResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Row> mRows = new ArrayList<>();

        void setRows(List<Row> rows) {
            mRows.clear();
            mRows.addAll(rows);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return mRows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_ZONE) {
                return new ZoneHolder(inflater.inflate(R.layout.item_timezone_search_result,
                        parent, false));
            }
            int layout = viewType == TYPE_MESSAGE
                    ? R.layout.item_timezone_search_message : R.layout.item_timezone_search_header;
            return new TextHolder(inflater.inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = mRows.get(position);
            if (holder instanceof ZoneHolder) {
                bindZone((ZoneHolder) holder, row.entry);
            } else {
                ((TextHolder) holder).text.setText(row.text);
            }
        }

        private void bindZone(ZoneHolder holder, TimeZoneSearchMatcher.Entry entry) {
            Context context = holder.itemView.getContext();
            String name = TextUtils.isEmpty(entry.displayName) ? entry.id : entry.displayName;
            holder.name.setText(name);
            // The zone ID is shown as the stable technical name; country adds the location.
            holder.detail.setText(TextUtils.isEmpty(entry.country)
                    ? entry.id : entry.country + " \u00b7 " + entry.id);
            holder.offset.setText(context.getString(R.string.timezone_search_offset_note,
                    TimeZoneSearchMatcher.formatOffset(entry.offsetMillis), mReferenceDateText));
            holder.itemView.setOnClickListener(v -> selectTimeZone(entry.id));
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }
    }
}
