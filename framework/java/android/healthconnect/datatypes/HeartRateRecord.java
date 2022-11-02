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

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Captures the user's heart rate. Each record represents a series of measurements. */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_HEART_RATE)
public class HeartRateRecord extends IntervalRecord {
    /** A class to represent heart rate samples */
    public static final class HeartRateSample {
        private final long mBeatsPerMinute;
        private final Instant mTime;

        /** Heart rate sample for entries of {@link HeartRateRecord} */
        public HeartRateSample(long beatsPerMinute, @NonNull Instant time) {
            Objects.requireNonNull(time);

            mBeatsPerMinute = beatsPerMinute;
            mTime = time;
        }

        /**
         * @return beats per minute for this sample
         */
        public long getBeatsPerMinute() {
            return mBeatsPerMinute;
        }

        /**
         * @return time at which this sample was recorded
         */
        @NonNull
        public Instant getTime() {
            return mTime;
        }
    }

    /**
     * Builder class for {@link HeartRateRecord}
     *
     * @see HeartRateRecord
     */
    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<HeartRateSample> mHeartRateSamples;
        private ZoneOffset mStartZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        private ZoneOffset mEndZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());

        /**
         * @param metadata Metadata to be associated with the record. See {@link Metadata}.
         * @param startTime Start time of this activity
         * @param endTime End time of this activity
         * @param heartRateSamples Samples of recorded heart rate
         * @throws IllegalArgumentException if {@code heartRateSamples} is empty
         */
        public Builder(
                @NonNull Metadata metadata,
                @NonNull Instant startTime,
                @NonNull Instant endTime,
                @NonNull List<HeartRateSample> heartRateSamples) {
            Objects.requireNonNull(metadata);
            Objects.requireNonNull(startTime);
            Objects.requireNonNull(endTime);
            Objects.requireNonNull(heartRateSamples);
            if (heartRateSamples.isEmpty()) {
                throw new IllegalArgumentException("record samples should not be empty");
            }

            mMetadata = metadata;
            mStartTime = startTime;
            mEndTime = endTime;
            mHeartRateSamples = heartRateSamples;
        }

        /**
         * Sets the zone offset of the user when the activity started. By default, the starting zone
         * offset is set the current zone offset.
         */
        @NonNull
        public Builder setStartZoneOffset(@NonNull ZoneOffset startZoneOffset) {
            Objects.requireNonNull(startZoneOffset);

            mStartZoneOffset = startZoneOffset;
            return this;
        }

        /**
         * Sets the zone offset of the user when the activity ended. By default, the end zone offset
         * is set the current zone offset.
         */
        @NonNull
        public Builder setEndZoneOffset(@NonNull ZoneOffset endZoneOffset) {
            Objects.requireNonNull(endZoneOffset);

            mEndZoneOffset = endZoneOffset;
            return this;
        }

        /**
         * @return Object of {@link HeartRateRecord}
         */
        @NonNull
        public HeartRateRecord build() {
            return new HeartRateRecord(
                    mMetadata,
                    mStartTime,
                    mStartZoneOffset,
                    mEndTime,
                    mEndZoneOffset,
                    mHeartRateSamples);
        }
    }

    private final List<HeartRateSample> mHeartRateSamples;

    private HeartRateRecord(
            @NonNull Metadata metadata,
            @NonNull Instant startTime,
            @NonNull ZoneOffset startZoneOffset,
            @NonNull Instant endTime,
            @NonNull ZoneOffset endZoneOffset,
            @NonNull List<HeartRateSample> heartRateSamples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);

        mHeartRateSamples = heartRateSamples;
    }

    /**
     * @return heart rate samples corresponding to this record
     */
    @NonNull
    public List<HeartRateSample> getSamples() {
        return mHeartRateSamples;
    }
}