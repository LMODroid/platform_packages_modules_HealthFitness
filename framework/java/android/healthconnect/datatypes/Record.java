/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.healthconnect.datatypes;

import android.annotation.NonNull;
import android.annotation.SystemApi;

import java.util.Objects;

public abstract class Record {
    private final Metadata mMetadata;
    @RecordTypeIdentifier.RecordType private final int mRecordIdentifier;

    /**
     * @param metadata Metadata to be associated with the record. See {@link Metadata}
     */
    Record(@NonNull Metadata metadata) {
        Identifier annotation = this.getClass().getAnnotation(Identifier.class);
        Objects.requireNonNull(annotation);
        mRecordIdentifier = annotation.recordIdentifier();
        mMetadata = metadata;
    }

    /**
     * @return {@link Metadata} for this record
     */
    @NonNull
    public Metadata getMetadata() {
        return mMetadata;
    }

    /**
     * TODO(b/249583483): Add permission so that only UI APK can access this
     *
     * @hide
     */
    @SystemApi
    @RecordTypeIdentifier.RecordType
    public int getRecordType() {
        return mRecordIdentifier;
    }
}