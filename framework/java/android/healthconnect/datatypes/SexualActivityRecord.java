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

import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Captures an occurrence of sexual activity. Each record is a single occurrence. ProtectionUsed
 * field is optional.
 */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_SEXUAL_ACTIVITY)
public final class SexualActivityRecord extends InstantRecord {

    private final int mProtectionUsed;

    /**
     * @param metadata Metadata to be associated with the record. See {@link Metadata}.
     * @param time Start time of this activity
     * @param zoneOffset Zone offset of the user when the activity started
     * @param protectionUsed ProtectionUsed of this activity
     */
    private SexualActivityRecord(
            @NonNull Metadata metadata,
            @NonNull Instant time,
            @NonNull ZoneOffset zoneOffset,
            @SexualActivityProtectionUsed.SexualActivityProtectionUsedTypes int protectionUsed) {
        super(metadata, time, zoneOffset);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(time);
        Objects.requireNonNull(zoneOffset);
        mProtectionUsed = protectionUsed;
    }

    /**
     * @return protectionUsed
     */
    @SexualActivityProtectionUsed.SexualActivityProtectionUsedTypes
    public int getProtectionUsed() {
        return mProtectionUsed;
    }

    /** Identifier for sexual activity protection used */
    public static final class SexualActivityProtectionUsed {
        public static final int PROTECTION_USED_UNKNOWN = 0;
        public static final int PROTECTION_USED_PROTECTED = 1;
        public static final int PROTECTION_USED_UNPROTECTED = 2;

        SexualActivityProtectionUsed() {}

        /** @hide */
        @IntDef({PROTECTION_USED_UNKNOWN, PROTECTION_USED_PROTECTED, PROTECTION_USED_UNPROTECTED})
        @Retention(RetentionPolicy.SOURCE)
        public @interface SexualActivityProtectionUsedTypes {}
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        SexualActivityRecord that = (SexualActivityRecord) o;
        return getProtectionUsed() == that.getProtectionUsed();
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getProtectionUsed());
    }

    /** Builder class for {@link SexualActivityRecord} */
    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private ZoneOffset mZoneOffset;
        private final int mProtectionUsed;

        /**
         * @param metadata Metadata to be associated with the record. See {@link Metadata}.
         * @param time Start time of this activity
         * @param protectionUsed Whether protection was used during sexual activity. Optional field,
         *     null if unknown. Allowed values: Protection.
         */
        public Builder(
                @NonNull Metadata metadata,
                @NonNull Instant time,
                @SexualActivityProtectionUsed.SexualActivityProtectionUsedTypes
                        int protectionUsed) {
            Objects.requireNonNull(metadata);
            Objects.requireNonNull(time);
            mMetadata = metadata;
            mTime = time;
            mZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
            mProtectionUsed = protectionUsed;
        }

        /** Sets the zone offset of the user when the activity happened */
        @NonNull
        public Builder setZoneOffset(@NonNull ZoneOffset zoneOffset) {
            Objects.requireNonNull(zoneOffset);
            mZoneOffset = zoneOffset;
            return this;
        }

        /**
         * @return Object of {@link SexualActivityRecord}
         */
        @NonNull
        public SexualActivityRecord build() {
            return new SexualActivityRecord(mMetadata, mTime, mZoneOffset, mProtectionUsed);
        }
    }
}