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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A failed ListenableFuture for AppSearch sessions on API < 31.
 * Used when PlatformStorage is unavailable and we don't fall back to LocalStorage
 * (which bundles libicing.so with 16 KB ELF alignment issues).
 */
public final class FailedAppSearchFuture implements ListenableFuture<AppSearchSession> {

    private final UnsupportedOperationException exception;

    public FailedAppSearchFuture(String message) {
        this.exception = new UnsupportedOperationException(message);
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        executor.execute(listener);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return true;
    }

    @Override
    public boolean isCancelled() {
        return true;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public AppSearchSession get() {
        throw exception;
    }

    @Override
    public AppSearchSession get(long timeout, TimeUnit unit) {
        throw exception;
    }
}
