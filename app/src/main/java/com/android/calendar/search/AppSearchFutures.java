/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.calendar.search;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Helper for creating failed AppSearch futures when PlatformStorage is unavailable.
 * Uses CallbackToFutureAdapter to avoid hand-written Future implementations.
 */
public final class AppSearchFutures {

    private AppSearchFutures() {}

    /**
     * Creates a failed future for AppSearch sessions on API < 31.
     */
    public static ListenableFuture<AppSearchSession> failedSessionFuture() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            completer.setException(
                new UnsupportedOperationException("AppSearch PlatformStorage requires Android 12+ (API 31)")
            );
            return "AppSearch PlatformStorage unavailable on API < 31";
        });
    }
}
