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
package android.health.connect.datatypes;

import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_POWER;

import android.annotation.NonNull;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.units.Power;
import android.health.connect.internal.datatypes.PowerRecordInternal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Captures the power generated by the user, e.g. during cycling or rowing with a power meter. */
@Identifier(recordIdentifier = RECORD_TYPE_POWER)
public final class PowerRecord extends IntervalRecord {
    private final List<PowerRecordSample> mPowerRecordSamples;

    /**
     * @param metadata Metadata to be associated with the record. See {@link Metadata}.
     * @param startTime Start time of this activity
     * @param startZoneOffset Zone offset of the user when the activity started
     * @param endTime End time of this activity
     * @param endZoneOffset Zone offset of the user when the activity finished
     * @param powerRecordSamples Samples of recorded PowerRecord
     */
    private PowerRecord(
            @NonNull Metadata metadata,
            @NonNull Instant startTime,
            @NonNull ZoneOffset startZoneOffset,
            @NonNull Instant endTime,
            @NonNull ZoneOffset endZoneOffset,
            @NonNull List<PowerRecordSample> powerRecordSamples) {
        super(metadata, startTime, startZoneOffset, endTime, endZoneOffset);
        Objects.requireNonNull(powerRecordSamples);
        ValidationUtils.validateSampleStartAndEndTime(
                startTime,
                endTime,
                powerRecordSamples.stream().map(PowerRecord.PowerRecordSample::getTime).toList());
        mPowerRecordSamples = powerRecordSamples;
    }

    /**
     * @return PowerRecord samples corresponding to this record
     */
    @NonNull
    public List<PowerRecordSample> getSamples() {
        return mPowerRecordSamples;
    }

    /**
     * Represents a single measurement of power. For example, using a power meter when exercising on
     * a stationary bike.
     */
    public static final class PowerRecordSample {
        private final Power mPower;
        private final Instant mTime;

        /**
         * PowerRecord sample for entries of {@link PowerRecord}
         *
         * @param power Power generated, in {@link Power} unit.
         * @param time The point in time when the measurement was taken.
         */
        public PowerRecordSample(@NonNull Power power, @NonNull Instant time) {
            Objects.requireNonNull(time);
            Objects.requireNonNull(power);
            ValidationUtils.requireInRange(power.getInWatts(), 0.0, 100000.0, "power");
            mTime = time;
            mPower = power;
        }

        /**
         * @return Power for this sample
         */
        @NonNull
        public Power getPower() {
            return mPower;
        }

        /**
         * @return time at which this sample was recorded
         */
        @NonNull
        public Instant getTime() {
            return mTime;
        }

        /**
         * Indicates whether some other object is "equal to" this one.
         *
         * @param object the reference object with which to compare.
         * @return {@code true} if this object is the same as the obj
         */
        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof PowerRecordSample)) return false;
            PowerRecordSample that = (PowerRecordSample) object;
            return Objects.equals(mPower, that.mPower)
                    && (mTime.toEpochMilli() == that.mTime.toEpochMilli());
        }

        /**
         * Returns a hash code value for the object.
         *
         * @return a hash code value for this object.
         */
        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), getPower(), getTime());
        }
    }

    /** Builder class for {@link PowerRecord} */
    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mStartTime;
        private final Instant mEndTime;
        private final List<PowerRecordSample> mPowerRecordSamples;
        private ZoneOffset mStartZoneOffset;
        private ZoneOffset mEndZoneOffset;

        /**
         * @param metadata Metadata to be associated with the record. See {@link Metadata}.
         * @param startTime Start time of this activity
         * @param endTime End time of this activity
         * @param powerRecordSamples Samples of recorded PowerRecord
         */
        public Builder(
                @NonNull Metadata metadata,
                @NonNull Instant startTime,
                @NonNull Instant endTime,
                @NonNull List<PowerRecordSample> powerRecordSamples) {
            Objects.requireNonNull(metadata);
            Objects.requireNonNull(startTime);
            Objects.requireNonNull(endTime);
            Objects.requireNonNull(powerRecordSamples);
            mMetadata = metadata;
            mStartTime = startTime;
            mEndTime = endTime;
            mPowerRecordSamples = powerRecordSamples;
            mStartZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(startTime);
            mEndZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(endTime);
        }

        /** Sets the zone offset of the user when the activity started */
        @NonNull
        public Builder setStartZoneOffset(@NonNull ZoneOffset startZoneOffset) {
            Objects.requireNonNull(startZoneOffset);
            mStartZoneOffset = startZoneOffset;
            return this;
        }

        /** Sets the zone offset of the user when the activity ended */
        @NonNull
        public Builder setEndZoneOffset(@NonNull ZoneOffset endZoneOffset) {
            Objects.requireNonNull(endZoneOffset);
            mEndZoneOffset = endZoneOffset;
            return this;
        }

        /** Sets the start zone offset of this record to system default. */
        @NonNull
        public Builder clearStartZoneOffset() {
            mStartZoneOffset = RecordUtils.getDefaultZoneOffset();
            return this;
        }

        /** Sets the start zone offset of this record to system default. */
        @NonNull
        public Builder clearEndZoneOffset() {
            mEndZoneOffset = RecordUtils.getDefaultZoneOffset();
            return this;
        }

        /**
         * @return Object of {@link PowerRecord}
         */
        @NonNull
        public PowerRecord build() {
            return new PowerRecord(
                    mMetadata,
                    mStartTime,
                    mStartZoneOffset,
                    mEndTime,
                    mEndZoneOffset,
                    mPowerRecordSamples);
        }
    }

    /** Metric identifier to get max power using aggregate APIs in {@link HealthConnectManager} */
    @NonNull
    public static final AggregationType<Power> POWER_MAX =
            new AggregationType<>(
                    AggregationType.AggregationTypeIdentifier.POWER_RECORD_POWER_MAX,
                    AggregationType.MAX,
                    RECORD_TYPE_POWER,
                    Power.class);

    /** Metric identifier to get min power using aggregate APIs in {@link HealthConnectManager} */
    @NonNull
    public static final AggregationType<Power> POWER_MIN =
            new AggregationType<>(
                    AggregationType.AggregationTypeIdentifier.POWER_RECORD_POWER_MIN,
                    AggregationType.MIN,
                    RECORD_TYPE_POWER,
                    Power.class);

    /** Metric identifier to get avg power using aggregate APIs in {@link HealthConnectManager} */
    @NonNull
    public static final AggregationType<Power> POWER_AVG =
            new AggregationType<>(
                    AggregationType.AggregationTypeIdentifier.POWER_RECORD_POWER_AVG,
                    AggregationType.AVG,
                    RECORD_TYPE_POWER,
                    Power.class);

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param object the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     */
    @Override
    public boolean equals(@NonNull Object object) {
        if (super.equals(object) && object instanceof PowerRecord) {
            PowerRecord other = (PowerRecord) object;
            if (getSamples().size() != other.getSamples().size()) return false;
            for (int idx = 0; idx < getSamples().size(); idx++) {
                if (!Objects.equals(
                                getSamples().get(idx).getPower(),
                                other.getSamples().get(idx).getPower())
                        || getSamples().get(idx).getTime().toEpochMilli()
                                != other.getSamples().get(idx).getTime().toEpochMilli()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getSamples());
    }

    /** @hide */
    @Override
    public PowerRecordInternal toRecordInternal() {
        PowerRecordInternal recordInternal =
                (PowerRecordInternal)
                        new PowerRecordInternal()
                                .setUuid(getMetadata().getId())
                                .setPackageName(getMetadata().getDataOrigin().getPackageName())
                                .setLastModifiedTime(
                                        getMetadata().getLastModifiedTime().toEpochMilli())
                                .setClientRecordId(getMetadata().getClientRecordId())
                                .setClientRecordVersion(getMetadata().getClientRecordVersion())
                                .setManufacturer(getMetadata().getDevice().getManufacturer())
                                .setModel(getMetadata().getDevice().getModel())
                                .setDeviceType(getMetadata().getDevice().getType())
                                .setRecordingMethod(getMetadata().getRecordingMethod());
        Set<PowerRecordInternal.PowerRecordSample> samples = new HashSet<>(getSamples().size());

        for (PowerRecord.PowerRecordSample powerRecordSample : getSamples()) {
            samples.add(
                    new PowerRecordInternal.PowerRecordSample(
                            powerRecordSample.getPower().getInWatts(),
                            powerRecordSample.getTime().toEpochMilli()));
        }
        recordInternal.setSamples(samples);
        recordInternal.setStartTime(getStartTime().toEpochMilli());
        recordInternal.setEndTime(getEndTime().toEpochMilli());
        recordInternal.setStartZoneOffset(getStartZoneOffset().getTotalSeconds());
        recordInternal.setEndZoneOffset(getEndZoneOffset().getTotalSeconds());

        return recordInternal;
    }
}
