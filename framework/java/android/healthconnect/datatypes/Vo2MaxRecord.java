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

import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/** Capture user's VO2 max score and optionally the measurement method. */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_VO2_MAX)
public final class Vo2MaxRecord extends InstantRecord {

    private final int mMeasurementMethod;
    private final double mVo2MillilitersPerMinuteKilogram;

    /**
     * @param metadata Metadata to be associated with the record. See {@link Metadata}.
     * @param time Start time of this activity
     * @param zoneOffset Zone offset of the user when the activity started
     * @param measurementMethod MeasurementMethod of this activity
     * @param vo2MillilitersPerMinuteKilogram Vo2MillilitersPerMinuteKilogram of this activity
     */
    private Vo2MaxRecord(
            @NonNull Metadata metadata,
            @NonNull Instant time,
            @NonNull ZoneOffset zoneOffset,
            @Vo2MaxMeasurementMethod.Vo2MaxMeasurementMethodTypes int measurementMethod,
            double vo2MillilitersPerMinuteKilogram) {
        super(metadata, time, zoneOffset);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(time);
        Objects.requireNonNull(zoneOffset);
        mMeasurementMethod = measurementMethod;
        mVo2MillilitersPerMinuteKilogram = vo2MillilitersPerMinuteKilogram;
    }

    /**
     * @return measurementMethod
     */
    @Vo2MaxMeasurementMethod.Vo2MaxMeasurementMethodTypes
    public int getMeasurementMethod() {
        return mMeasurementMethod;
    }

    /**
     * @return vo2MillilitersPerMinuteKilogram
     */
    public double getVo2MillilitersPerMinuteKilogram() {
        return mVo2MillilitersPerMinuteKilogram;
    }

    /** Identifier for V02 max measurement method */
    public static final class Vo2MaxMeasurementMethod {
        public static final int MEASUREMENT_METHOD_OTHER = 0;
        public static final int MEASUREMENT_METHOD_METABOLIC_CART = 1;
        public static final int MEASUREMENT_METHOD_HEART_RATE_RATIO = 2;
        public static final int MEASUREMENT_METHOD_COOPER_TEST = 3;
        public static final int MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST = 4;
        public static final int MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST = 5;

        Vo2MaxMeasurementMethod() {}

        /** @hide */
        @IntDef({
            MEASUREMENT_METHOD_OTHER,
            MEASUREMENT_METHOD_METABOLIC_CART,
            MEASUREMENT_METHOD_HEART_RATE_RATIO,
            MEASUREMENT_METHOD_COOPER_TEST,
            MEASUREMENT_METHOD_MULTISTAGE_FITNESS_TEST,
            MEASUREMENT_METHOD_ROCKPORT_FITNESS_TEST
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Vo2MaxMeasurementMethodTypes {}
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
        Vo2MaxRecord that = (Vo2MaxRecord) o;
        return getMeasurementMethod() == that.getMeasurementMethod()
                && Double.compare(
                                that.getVo2MillilitersPerMinuteKilogram(),
                                getVo2MillilitersPerMinuteKilogram())
                        == 0;
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(), getMeasurementMethod(), getVo2MillilitersPerMinuteKilogram());
    }

    /** Builder class for {@link Vo2MaxRecord} */
    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private ZoneOffset mZoneOffset;
        private final int mMeasurementMethod;
        private final double mVo2MillilitersPerMinuteKilogram;

        /**
         * @param metadata Metadata to be associated with the record. See {@link Metadata}.
         * @param time Start time of this activity
         * @param measurementMethod VO2 max measurement method. Optional field. Allowed values:
         *     {@link Vo2MaxMeasurementMethod}.
         * @param vo2MillilitersPerMinuteKilogram Maximal aerobic capacity (VO2 max) in milliliters.
         *     Required field. Valid range: 0-100.
         */
        public Builder(
                @NonNull Metadata metadata,
                @NonNull Instant time,
                @Vo2MaxMeasurementMethod.Vo2MaxMeasurementMethodTypes int measurementMethod,
                @FloatRange(from = 0, to = 100) double vo2MillilitersPerMinuteKilogram) {
            Objects.requireNonNull(metadata);
            Objects.requireNonNull(time);
            mMetadata = metadata;
            mTime = time;
            mZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
            mMeasurementMethod = measurementMethod;
            mVo2MillilitersPerMinuteKilogram = vo2MillilitersPerMinuteKilogram;
        }

        /** Sets the zone offset of the user when the activity happened */
        @NonNull
        public Builder setZoneOffset(@NonNull ZoneOffset zoneOffset) {
            Objects.requireNonNull(zoneOffset);
            mZoneOffset = zoneOffset;
            return this;
        }

        /**
         * @return Object of {@link Vo2MaxRecord}
         */
        @NonNull
        public Vo2MaxRecord build() {
            return new Vo2MaxRecord(
                    mMetadata,
                    mTime,
                    mZoneOffset,
                    mMeasurementMethod,
                    mVo2MillilitersPerMinuteKilogram);
        }
    }
}