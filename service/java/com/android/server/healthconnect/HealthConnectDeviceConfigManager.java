/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect;

import android.annotation.NonNull;
import android.content.Context;
import android.provider.DeviceConfig;

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;

/**
 * Singleton class to provide values and listen changes of settings flags.
 *
 * @hide
 */
public class HealthConnectDeviceConfigManager implements DeviceConfig.OnPropertiesChangedListener {
    // TODO(b/268472587): switch to DeviceInfo.NAMESPACE_HEALTH_CONNECT
    public static final String HEALTH_CONNECT_FLAGS_NAMESPACE = "health_connect";

    public static final String EXERCISE_ROUTE_FEATURE_FLAG = "exercise_routes_enable";

    public static final boolean EXERCISE_ROUTE_DEFAULT_FLAG_VALUE = true;

    private static HealthConnectDeviceConfigManager sDeviceConfigManager;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mExerciseRouteEnabled =
            DeviceConfig.getBoolean(
                    HEALTH_CONNECT_FLAGS_NAMESPACE,
                    EXERCISE_ROUTE_FEATURE_FLAG,
                    EXERCISE_ROUTE_DEFAULT_FLAG_VALUE);

    @NonNull
    static void initializeInstance(Context context) {
        if (sDeviceConfigManager == null) {
            sDeviceConfigManager = new HealthConnectDeviceConfigManager();
            DeviceConfig.addOnPropertiesChangedListener(
                    HEALTH_CONNECT_FLAGS_NAMESPACE,
                    context.getMainExecutor(),
                    sDeviceConfigManager);
        }
    }

    /** Returns initialised instance of this class. */
    @NonNull
    public static HealthConnectDeviceConfigManager getInitialisedInstance() {
        Objects.requireNonNull(sDeviceConfigManager);

        return sDeviceConfigManager;
    }

    /** Returns if operations with exercise route are enabled. */
    public boolean isExerciseRouteFeatureEnabled() {
        synchronized (mLock) {
            return mExerciseRouteEnabled;
        }
    }

    @Override
    public void onPropertiesChanged(DeviceConfig.Properties properties) {
        synchronized (mLock) {
            if (!properties.getNamespace().equals(HEALTH_CONNECT_FLAGS_NAMESPACE)) {
                return;
            }
            for (String name : properties.getKeyset()) {
                if (name == null) {
                    continue;
                }

                if (name.equals(EXERCISE_ROUTE_FEATURE_FLAG)) {
                    mExerciseRouteEnabled =
                            properties.getBoolean(
                                    EXERCISE_ROUTE_FEATURE_FLAG, EXERCISE_ROUTE_DEFAULT_FLAG_VALUE);
                }
            }
        }
    }
}
