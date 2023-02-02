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

package android.health.connect.aidl;

import android.annotation.NonNull;
import android.health.connect.ChangeLogsResponse;
import android.health.connect.internal.datatypes.utils.InternalExternalRecordConverter;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** @hide */
public class ChangeLogsResponseParcel implements Parcelable {
    public static final Creator<ChangeLogsResponseParcel> CREATOR =
            new Creator<ChangeLogsResponseParcel>() {
                @Override
                public ChangeLogsResponseParcel createFromParcel(Parcel in) {
                    return new ChangeLogsResponseParcel(in);
                }

                @Override
                public ChangeLogsResponseParcel[] newArray(int size) {
                    return new ChangeLogsResponseParcel[size];
                }
            };
    private final RecordsParcel mUpsertedRecords;
    private final List<ChangeLogsResponse.DeletedLog> mDeletedLogs;
    private final String mNextChangesToken;
    private final boolean mHasMorePages;

    protected ChangeLogsResponseParcel(Parcel in) {
        mUpsertedRecords =
                in.readParcelable(RecordsParcel.class.getClassLoader(), RecordsParcel.class);
        int size = in.readInt();
        List<ChangeLogsResponse.DeletedLog> deletedLogs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = in.readString();
            long time = in.readLong();
            deletedLogs.add(new ChangeLogsResponse.DeletedLog(id, time));
        }
        mDeletedLogs = deletedLogs;
        mNextChangesToken = in.readString();
        mHasMorePages = in.readBoolean();
    }

    public ChangeLogsResponseParcel(
            RecordsParcel upsertedRecords,
            List<ChangeLogsResponse.DeletedLog> deletedLogs,
            String nextChangesToken,
            boolean hasMorePages) {
        mUpsertedRecords = upsertedRecords;
        mDeletedLogs = deletedLogs;
        mNextChangesToken = nextChangesToken;
        mHasMorePages = hasMorePages;
    }

    public ChangeLogsResponse getChangeLogsResponse()
            throws InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException,
                    NoSuchMethodException {
        return new ChangeLogsResponse(
                InternalExternalRecordConverter.getInstance()
                        .getExternalRecords(mUpsertedRecords.getRecords()),
                mDeletedLogs,
                mNextChangesToken,
                mHasMorePages);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUpsertedRecords, 0);
        dest.writeInt(mDeletedLogs.size());
        for (ChangeLogsResponse.DeletedLog deletedLog : mDeletedLogs) {
            dest.writeString(deletedLog.getDeletedRecordId());
            dest.writeLong(deletedLog.getDeletedTime().toEpochMilli());
        }
        dest.writeString(mNextChangesToken);
        dest.writeBoolean(mHasMorePages);
    }
}
