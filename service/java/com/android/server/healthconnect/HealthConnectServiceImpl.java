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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.healthconnect.Constants.DEFAULT_LONG;
import static android.healthconnect.Constants.READ;
import static android.healthconnect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.healthconnect.HealthConnectDataState.RESTORE_ERROR_FETCHING_DATA;
import static android.healthconnect.HealthConnectDataState.RESTORE_ERROR_NONE;
import static android.healthconnect.HealthConnectDataState.RESTORE_STATE_IDLE;
import static android.healthconnect.HealthConnectDataState.RESTORE_STATE_IN_PROGRESS;
import static android.healthconnect.HealthConnectDataState.RESTORE_STATE_PENDING;
import static android.healthconnect.HealthConnectManager.DATA_DOWNLOAD_COMPLETE;
import static android.healthconnect.HealthConnectManager.DATA_DOWNLOAD_FAILED;
import static android.healthconnect.HealthConnectManager.DATA_DOWNLOAD_STATE_UNKNOWN;
import static android.healthconnect.HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.healthconnect.AccessLog;
import android.healthconnect.Constants;
import android.healthconnect.FetchDataOriginsPriorityOrderResponse;
import android.healthconnect.HealthConnectDataState;
import android.healthconnect.HealthConnectException;
import android.healthconnect.HealthConnectManager;
import android.healthconnect.HealthConnectManager.DataDownloadState;
import android.healthconnect.HealthDataCategory;
import android.healthconnect.HealthPermissions;
import android.healthconnect.aidl.AccessLogsResponseParcel;
import android.healthconnect.aidl.ActivityDatesRequestParcel;
import android.healthconnect.aidl.ActivityDatesResponseParcel;
import android.healthconnect.aidl.AggregateDataRequestParcel;
import android.healthconnect.aidl.ApplicationInfoResponseParcel;
import android.healthconnect.aidl.ChangeLogTokenRequestParcel;
import android.healthconnect.aidl.ChangeLogTokenResponseParcel;
import android.healthconnect.aidl.ChangeLogsRequestParcel;
import android.healthconnect.aidl.ChangeLogsResponseParcel;
import android.healthconnect.aidl.DeleteUsingFiltersRequestParcel;
import android.healthconnect.aidl.GetPriorityResponseParcel;
import android.healthconnect.aidl.HealthConnectExceptionParcel;
import android.healthconnect.aidl.IAccessLogsResponseCallback;
import android.healthconnect.aidl.IActivityDatesResponseCallback;
import android.healthconnect.aidl.IAggregateRecordsResponseCallback;
import android.healthconnect.aidl.IApplicationInfoResponseCallback;
import android.healthconnect.aidl.IChangeLogsResponseCallback;
import android.healthconnect.aidl.IDataStagingFinishedCallback;
import android.healthconnect.aidl.IEmptyResponseCallback;
import android.healthconnect.aidl.IGetChangeLogTokenCallback;
import android.healthconnect.aidl.IGetHealthConnectDataStateCallback;
import android.healthconnect.aidl.IGetPriorityResponseCallback;
import android.healthconnect.aidl.IHealthConnectService;
import android.healthconnect.aidl.IInsertRecordsResponseCallback;
import android.healthconnect.aidl.IMigrationCallback;
import android.healthconnect.aidl.IReadRecordsResponseCallback;
import android.healthconnect.aidl.IRecordTypeInfoResponseCallback;
import android.healthconnect.aidl.InsertRecordsResponseParcel;
import android.healthconnect.aidl.ReadRecordsRequestParcel;
import android.healthconnect.aidl.ReadRecordsResponseParcel;
import android.healthconnect.aidl.RecordIdFiltersParcel;
import android.healthconnect.aidl.RecordTypeInfoResponseParcel;
import android.healthconnect.aidl.RecordsParcel;
import android.healthconnect.aidl.UpdatePriorityRequestParcel;
import android.healthconnect.datatypes.AppInfo;
import android.healthconnect.datatypes.DataOrigin;
import android.healthconnect.datatypes.Record;
import android.healthconnect.internal.datatypes.RecordInternal;
import android.healthconnect.internal.datatypes.utils.AggregationTypeIdMapper;
import android.healthconnect.internal.datatypes.utils.RecordMapper;
import android.healthconnect.internal.datatypes.utils.RecordTypePermissionCategoryMapper;
import android.healthconnect.migration.MigrationEntity;
import android.healthconnect.migration.MigrationException;
import android.healthconnect.restore.StageRemoteDataException;
import android.healthconnect.restore.StageRemoteDataRequest;
import android.os.Binder;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.migration.DataMigrationManager;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.storage.AutoDeleteService;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.request.AggregateTransactionRequest;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.request.UpsertTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * IHealthConnectService's implementation
 *
 * @hide
 */
final class HealthConnectServiceImpl extends IHealthConnectService.Stub {
    // Key for storing the current data download state
    @VisibleForTesting static final String DATA_DOWNLOAD_STATE_KEY = "DATA_DOWNLOAD_STATE_KEY";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        INTERNAL_RESTORE_STATE_UNKNOWN,
        INTERNAL_RESTORE_STATE_WAITING_FOR_STAGING,
        INTERNAL_RESTORE_STATE_STAGING_IN_PROGRESS,
        INTERNAL_RESTORE_STATE_STAGING_DONE,
        INTERNAL_RESTORE_STATE_MERGING_IN_PROGRESS,
        INTERNAL_RESTORE_STATE_MERGING_DONE
    })
    @interface InternalRestoreState {}

    // The below values for the IntDef are defined in chronological order of the restore process.
    static final int INTERNAL_RESTORE_STATE_UNKNOWN = 0;
    static final int INTERNAL_RESTORE_STATE_WAITING_FOR_STAGING = 1;
    static final int INTERNAL_RESTORE_STATE_STAGING_IN_PROGRESS = 2;
    static final int INTERNAL_RESTORE_STATE_STAGING_DONE = 3;
    static final int INTERNAL_RESTORE_STATE_MERGING_IN_PROGRESS = 4;
    static final int INTERNAL_RESTORE_STATE_MERGING_DONE = 5;
    private static final String TAG = "HealthConnectService";
    // Permission for test api for deleting staged data
    private static final String DELETE_STAGED_HEALTH_CONNECT_REMOTE_DATA_PERMISSION =
            "android.permission.DELETE_STAGED_HEALTH_CONNECT_REMOTE_DATA";
    // Key for storing the current data restore state on disk.
    private static final String DATA_RESTORE_STATE_KEY = "data_restore_state_key";
    // Key for storing the error restoring HC data.
    private static final String DATA_RESTORE_ERROR_KEY = "data_restore_error_key";

    private final TransactionManager mTransactionManager;
    private final HealthConnectPermissionHelper mPermissionHelper;
    private final FirstGrantTimeManager mFirstGrantTimeManager;
    private final Context mContext;
    private final PermissionManager mPermissionManager;

    HealthConnectServiceImpl(
            TransactionManager transactionManager,
            HealthConnectPermissionHelper permissionHelper,
            FirstGrantTimeManager firstGrantTimeManager,
            Context context) {
        mTransactionManager = transactionManager;
        mPermissionHelper = permissionHelper;
        mFirstGrantTimeManager = firstGrantTimeManager;
        mContext = context;
        mPermissionManager = mContext.getSystemService(PermissionManager.class);
    }

    @Override
    public void grantHealthPermission(
            @NonNull String packageName, @NonNull String permissionName, @NonNull UserHandle user) {
        mPermissionHelper.grantHealthPermission(packageName, permissionName, user);
    }

    @Override
    public void revokeHealthPermission(
            @NonNull String packageName,
            @NonNull String permissionName,
            @Nullable String reason,
            @NonNull UserHandle user) {
        mPermissionHelper.revokeHealthPermission(packageName, permissionName, reason, user);
    }

    @Override
    public void revokeAllHealthPermissions(
            @NonNull String packageName, @Nullable String reason, @NonNull UserHandle user) {
        mPermissionHelper.revokeAllHealthPermissions(packageName, reason, user);
    }

    @Override
    public List<String> getGrantedHealthPermissions(
            @NonNull String packageName, @NonNull UserHandle user) {
        return mPermissionHelper.getGrantedHealthPermissions(packageName, user);
    }

    @Override
    public long getHistoricalAccessStartDateInMilliseconds(
            @NonNull String packageName, @NonNull UserHandle userHandle) {
        Instant date = mPermissionHelper.getHealthDataStartDateAccess(packageName, userHandle);
        if (date == null) {
            return Constants.DEFAULT_LONG;
        } else {
            return date.toEpochMilli();
        }
    }

    /**
     * Inserts {@code recordsParcel} into the HealthConnect database.
     *
     * @param recordsParcel parcel for list of records to be inserted.
     * @param callback Callback to receive result of performing this operation. The keys returned in
     *     {@link InsertRecordsResponseParcel} are the unique IDs of the input records. The values
     *     are in same order as {@code record}. In case of an error or a permission failure the
     *     HealthConnect service, {@link IInsertRecordsResponseCallback#onError} will be invoked
     *     with a {@link HealthConnectExceptionParcel}.
     */
    @Override
    public void insertRecords(
            @NonNull String packageName,
            @NonNull RecordsParcel recordsParcel,
            @NonNull IInsertRecordsResponseCallback callback) {
        List<RecordInternal<?>> recordInternals = recordsParcel.getRecords();
        int uid = Binder.getCallingUid();
        enforceRecordWritePermissionForRecords(recordInternals, uid);
        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        List<String> uuids =
                                mTransactionManager.insertAll(
                                        new UpsertTransactionRequest(
                                                packageName,
                                                recordInternals,
                                                mContext,
                                                /* isInsertRequest */ true));
                        callback.onResult(new InsertRecordsResponseParcel(uuids));
                        HealthConnectThreadScheduler.scheduleInternalTask(
                                () ->
                                        ActivityDateHelper.getInstance()
                                                .insertRecordDate(recordsParcel.getRecords()));
                        finishDataDeliveryWriteRecords(recordInternals, uid);
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                false);
    }

    /**
     * Returns aggregation results based on the {@code request} into the HealthConnect database.
     *
     * @param packageName name of the package inserting the record.
     * @param request represents the request using which the aggregation is to be performed.
     * @param callback Callback to receive result of performing this operation.
     */
    public void aggregateRecords(
            String packageName,
            AggregateDataRequestParcel request,
            IAggregateRecordsResponseCallback callback) {
        List<Integer> recordTypesToTest = new ArrayList<>();
        for (int aggregateId : request.getAggregateIds()) {
            recordTypesToTest.addAll(
                    AggregationTypeIdMapper.getInstance()
                            .getAggregationTypeFor(aggregateId)
                            .getApplicableRecordTypeIds());
        }
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        boolean holdsDataManagementPermission = hasDataManagementPermission(uid, pid);
        if (!holdsDataManagementPermission) {
            enforceRecordReadPermission(recordTypesToTest, uid);
        }

        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        callback.onResult(
                                new AggregateTransactionRequest(packageName, request)
                                        .getAggregateDataResponseParcel());
                        finishDataDeliveryRead(recordTypesToTest, uid);
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                holdsDataManagementPermission);
    }

    /**
     * Read records {@code recordsParcel} from HealthConnect database.
     *
     * @param packageName packageName of calling app.
     * @param request ReadRecordsRequestParcel is parcel for the request object containing {@link
     *     RecordIdFiltersParcel}.
     * @param callback Callback to receive result of performing this operation. The records are
     *     returned in {@link RecordsParcel} . In case of an error or a permission failure the
     *     HealthConnect service, {@link IReadRecordsResponseCallback#onError} will be invoked with
     *     a {@link HealthConnectExceptionParcel}.
     */
    @Override
    public void readRecords(
            @NonNull String packageName,
            @NonNull ReadRecordsRequestParcel request,
            @NonNull IReadRecordsResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        boolean holdsDataManagementPermission = hasDataManagementPermission(uid, pid);
        AtomicBoolean enforceSelfRead = new AtomicBoolean();
        if (!holdsDataManagementPermission) {
            enforceSelfRead.set(enforceRecordReadPermission(uid, request.getRecordType()));
        }

        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        try {
                            Pair<List<RecordInternal<?>>, Long> readRecordsResponse =
                                    mTransactionManager.readRecordsAndGetNextToken(
                                            new ReadTransactionRequest(
                                                    packageName, request, enforceSelfRead.get()));
                            long pageToken =
                                    request.getRecordIdFiltersParcel() == null
                                            ? readRecordsResponse.second
                                            : DEFAULT_LONG;

                            if (Constants.DEBUG) {
                                Slog.d(TAG, "pageToken: " + pageToken);
                            }

                            callback.onResult(
                                    new ReadRecordsResponseParcel(
                                            new RecordsParcel(readRecordsResponse.first),
                                            pageToken));
                            // Calls from controller APK should not be recorded in access logs.
                            if (!holdsDataManagementPermission) {
                                HealthConnectThreadScheduler.scheduleInternalTask(
                                        () ->
                                                AccessLogsHelper.getInstance()
                                                        .addAccessLog(
                                                                packageName,
                                                                Collections.singletonList(
                                                                        request.getRecordType()),
                                                                READ));
                            }
                            finishDataDeliveryRead(request.getRecordType(), uid);
                        } catch (TypeNotPresentException exception) {
                            // All the requested package names are not present, so simply
                            // return an empty list
                            if (ReadTransactionRequest.TYPE_NOT_PRESENT_PACKAGE_NAME.equals(
                                    exception.typeName())) {
                                callback.onResult(
                                        new ReadRecordsResponseParcel(
                                                new RecordsParcel(new ArrayList<>()),
                                                DEFAULT_LONG));
                            } else {
                                throw exception;
                            }
                        }
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "Exception: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                holdsDataManagementPermission);
    }

    /**
     * Updates {@code recordsParcel} into the HealthConnect database.
     *
     * @param recordsParcel parcel for list of records to be updated.
     * @param callback Callback to receive result of performing this operation. In case of an error
     *     or a permission failure the HealthConnect service, {@link IEmptyResponseCallback#onError}
     *     will be invoked with a {@link HealthConnectException}.
     */
    @Override
    public void updateRecords(
            @NonNull String packageName,
            @NonNull RecordsParcel recordsParcel,
            @NonNull IEmptyResponseCallback callback) {
        int uid = Binder.getCallingUid();
        List<RecordInternal<?>> recordInternals = recordsParcel.getRecords();
        enforceRecordWritePermissionForRecords(recordInternals, uid);
        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        mTransactionManager.updateAll(
                                new UpsertTransactionRequest(
                                        packageName,
                                        recordInternals,
                                        mContext,
                                        /* isInsertRequest */ false));
                        callback.onResult();
                        finishDataDeliveryWriteRecords(recordInternals, uid);
                    } catch (SecurityException securityException) {
                        tryAndThrowException(
                                callback, securityException, HealthConnectException.ERROR_SECURITY);
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SqlException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (IllegalArgumentException illegalArgumentException) {
                        Slog.e(TAG, "Exception: ", illegalArgumentException);
                        tryAndThrowException(
                                callback,
                                illegalArgumentException,
                                HealthConnectException.ERROR_INVALID_ARGUMENT);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                false);
    }

    /**
     * @see HealthConnectManager#getChangeLogToken
     */
    @Override
    public void getChangeLogToken(
            @NonNull String packageName,
            @NonNull ChangeLogTokenRequestParcel request,
            @NonNull IGetChangeLogTokenCallback callback) {
        int uid = Binder.getCallingUid();
        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        callback.onResult(
                                new ChangeLogTokenResponseParcel(
                                        ChangeLogsRequestHelper.getInstance()
                                                .getToken(packageName, request)));
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception e) {
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                false);
    }

    /**
     * @hide
     * @see HealthConnectManager#getChangeLogs
     */
    @Override
    public void getChangeLogs(
            @NonNull String packageName,
            @NonNull ChangeLogsRequestParcel token,
            IChangeLogsResponseCallback callback) {
        int uid = Binder.getCallingUid();
        ChangeLogsRequestHelper.TokenRequest changeLogsTokenRequest =
                ChangeLogsRequestHelper.getRequest(packageName, token.getToken());
        enforceRecordReadPermission(changeLogsTokenRequest.getRecordTypes(), uid);
        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        final ChangeLogsHelper.ChangeLogsResponse changeLogsResponse =
                                ChangeLogsHelper.getInstance()
                                        .getChangeLogs(changeLogsTokenRequest, token.getPageSize());

                        List<RecordInternal<?>> recordInternals =
                                mTransactionManager.readRecords(
                                        new ReadTransactionRequest(
                                                ChangeLogsHelper.getRecordTypeToInsertedUuids(
                                                        changeLogsResponse.getChangeLogsMap())));
                        callback.onResult(
                                new ChangeLogsResponseParcel(
                                        new RecordsParcel(recordInternals),
                                        ChangeLogsHelper.getDeletedIds(
                                                changeLogsResponse.getChangeLogsMap()),
                                        changeLogsResponse.getNextPageToken(),
                                        changeLogsResponse.hasMorePages()));
                        finishDataDeliveryRead(changeLogsTokenRequest.getRecordTypes(), uid);
                    } catch (IllegalArgumentException illegalArgumentException) {
                        Slog.e(TAG, "IllegalArgumentException: ", illegalArgumentException);
                        tryAndThrowException(
                                callback,
                                illegalArgumentException,
                                HealthConnectException.ERROR_INVALID_ARGUMENT);
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception exception) {
                        Slog.e(TAG, "Exception: ", exception);
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                false);
    }

    /**
     * API to delete records based on {@code request}
     *
     * <p>NOTE: Internally we only need a single API to handle deletes as SDK code transform all its
     * delete requests to {@link DeleteUsingFiltersRequestParcel}
     */
    @Override
    public void deleteUsingFilters(
            @NonNull String packageName,
            @NonNull DeleteUsingFiltersRequestParcel request,
            @NonNull IEmptyResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        boolean holdsDataManagementPermission = hasDataManagementPermission(uid, pid);

        List<Integer> recordTypeIdsToDelete =
                (!request.getRecordTypeFilters().isEmpty())
                        ? request.getRecordTypeFilters()
                        : new ArrayList<>(
                                RecordMapper.getInstance()
                                        .getRecordIdToExternalRecordClassMap()
                                        .keySet());

        if (!holdsDataManagementPermission) {
            enforceRecordWritePermission(recordTypeIdsToDelete, uid);
        }

        HealthConnectThreadScheduler.schedule(
                mContext,
                () -> {
                    try {
                        mTransactionManager.deleteAll(
                                new DeleteTransactionRequest(packageName, request, mContext)
                                        .setHasManageHealthDataPermission(
                                                hasDataManagementPermission(uid, pid)));
                        callback.onResult();
                        finishDataDeliveryWrite(recordTypeIdsToDelete, uid);
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (IllegalArgumentException illegalArgumentException) {
                        Slog.e(TAG, "SQLiteException: ", illegalArgumentException);
                        tryAndThrowException(
                                callback,
                                illegalArgumentException,
                                HealthConnectException.ERROR_SECURITY);
                    } catch (Exception exception) {
                        Slog.e(TAG, "Exception: ", exception);
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                },
                uid,
                holdsDataManagementPermission);
    }

    /** API to get Priority for {@code dataCategory} */
    @Override
    public void getCurrentPriority(
            @NonNull String packageName,
            @HealthDataCategory.Type int dataCategory,
            @NonNull IGetPriorityResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);
        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        List<DataOrigin> dataOriginInPriorityOrder =
                                HealthDataCategoryPriorityHelper.getInstance()
                                        .getPriorityOrder(dataCategory)
                                        .stream()
                                        .map(
                                                (name) ->
                                                        new DataOrigin.Builder()
                                                                .setPackageName(name)
                                                                .build())
                                        .collect(Collectors.toList());
                        callback.onResult(
                                new GetPriorityResponseParcel(
                                        new FetchDataOriginsPriorityOrderResponse(
                                                dataOriginInPriorityOrder)));
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception exception) {
                        Slog.e(TAG, "Exception: ", exception);
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    /** API to update priority for permission category(ies) */
    @Override
    public void updatePriority(
            @NonNull String packageName,
            @NonNull UpdatePriorityRequestParcel updatePriorityRequest,
            @NonNull IEmptyResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);
        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        HealthDataCategoryPriorityHelper.getInstance()
                                .setPriorityOrder(
                                        updatePriorityRequest.getDataCategory(),
                                        updatePriorityRequest.getPackagePriorityOrder());
                        callback.onResult();
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception exception) {
                        Slog.e(TAG, "Exception: ", exception);
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    @Override
    public void setRecordRetentionPeriodInDays(
            int days, @NonNull UserHandle user, IEmptyResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);
        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        AutoDeleteService.setRecordRetentionPeriodInDays(
                                days, user.getIdentifier());
                        callback.onResult();
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SQLiteException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception exception) {
                        Slog.e(TAG, "Exception: ", exception);
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    @Override
    public int getRecordRetentionPeriodInDays(@NonNull UserHandle user) {
        try {
            mContext.enforceCallingPermission(MANAGE_HEALTH_DATA_PERMISSION, null);
            return AutoDeleteService.getRecordRetentionPeriodInDays(user.getIdentifier());
        } catch (Exception e) {
            if (e instanceof SecurityException) {
                throw e;
            }
            Slog.e(TAG, "Unable to get record retention period for " + user);
        }

        throw new RuntimeException();
    }

    /**
     * Returns information, represented by {@code ApplicationInfoResponse}, for all the packages
     * that have contributed to the health connect DB.
     *
     * @param callback Callback to receive result of performing this operation. In case of an error
     *     or a permission failure the HealthConnect service, {@link IEmptyResponseCallback#onError}
     *     will be invoked with a {@link HealthConnectException}.
     */
    @Override
    public void getContributorApplicationsInfo(@NonNull IApplicationInfoResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);
        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        List<AppInfo> applicationInfos =
                                AppInfoHelper.getInstance().getApplicationInfos();

                        callback.onResult(new ApplicationInfoResponseParcel(applicationInfos));
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SqlException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    /** Retrieves {@link android.healthconnect.RecordTypeInfoResponse} for each RecordType. */
    @Override
    public void queryAllRecordTypesInfo(@NonNull IRecordTypeInfoResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);
        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        callback.onResult(
                                new RecordTypeInfoResponseParcel(
                                        getPopulatedRecordTypeInfoResponses()));
                    } catch (SQLiteException sqLiteException) {
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_IO);
                    } catch (Exception exception) {
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    /**
     * @see HealthConnectManager#queryAccessLogs
     */
    @Override
    public void queryAccessLogs(@NonNull String packageName, IAccessLogsResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);

        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        final List<AccessLog> accessLogsList =
                                AccessLogsHelper.getInstance().queryAccessLogs();
                        callback.onResult(new AccessLogsResponseParcel(accessLogsList));
                    } catch (Exception exception) {
                        Slog.e(TAG, "Exception: ", exception);
                        tryAndThrowException(
                                callback, exception, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    /**
     * Returns a list of unique dates for which the database has at least one entry
     *
     * @param activityDatesRequestParcel Parcel request containing records classes
     * @param callback Callback to receive result of performing this operation. The results are
     *     returned in {@link List<LocalDate>} . In case of an error or a permission failure the
     *     HealthConnect service, {@link IActivityDatesResponseCallback#onError} will be invoked
     *     with a {@link HealthConnectExceptionParcel}.
     */
    @Override
    public void getActivityDates(
            @NonNull ActivityDatesRequestParcel activityDatesRequestParcel,
            IActivityDatesResponseCallback callback) {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        mContext.enforcePermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid, null);

        HealthConnectThreadScheduler.scheduleControllerTask(
                () -> {
                    try {
                        List<LocalDate> localDates =
                                ActivityDateHelper.getInstance()
                                        .getActivityDates(
                                                activityDatesRequestParcel.getRecordTypes());

                        callback.onResult(new ActivityDatesResponseParcel(localDates));
                    } catch (SQLiteException sqLiteException) {
                        Slog.e(TAG, "SqlException: ", sqLiteException);
                        tryAndThrowException(
                                callback, sqLiteException, HealthConnectException.ERROR_SECURITY);
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        tryAndThrowException(callback, e, HealthConnectException.ERROR_INTERNAL);
                    }
                });
    }

    // TODO(b/265780725): Update javadocs and ensure that the caller handles SHOW_MIGRATION_INFO
    // intent.
    @Override
    public void startMigration(IMigrationCallback callback) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA,
                "Caller does not have " + Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        // TODO(b/265000849): Start the migration
                        callback.onSuccess();
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        // TODO(b/263897830): Send errors properly
                        tryAndThrowException(callback, e, MigrationException.ERROR_UNKNOWN, null);
                    }
                });
    }

    // TODO(b/265780725): Update javadocs and ensure that the caller handles SHOW_MIGRATION_INFO
    // intent.
    @Override
    public void finishMigration(IMigrationCallback callback) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA,
                "Caller does not have " + Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        // TODO(b/264401271): Finish the migration
                        callback.onSuccess();
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        // TODO(b/263897830): Send errors properly
                        tryAndThrowException(callback, e, MigrationException.ERROR_UNKNOWN, null);
                    }
                });
    }

    // TODO(b/265780725): Update javadocs and ensure that the caller handles SHOW_MIGRATION_INFO
    // intent.
    @Override
    public void writeMigrationData(List<MigrationEntity> entities, IMigrationCallback callback) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA,
                "Caller does not have " + Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        getDataMigrationManager(getCallingUserHandle()).apply(entities);
                        callback.onSuccess();
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception: ", e);
                        // TODO(b/263897830): Send errors properly
                        tryAndThrowException(callback, e, MigrationException.ERROR_UNKNOWN, null);
                    }
                });
    }

    /**
     * @see HealthConnectManager#stageAllHealthConnectRemoteData
     */
    @Override
    public void stageAllHealthConnectRemoteData(
            @NonNull StageRemoteDataRequest stageRemoteDataRequest,
            @NonNull UserHandle userHandle,
            @NonNull IDataStagingFinishedCallback callback) {
        mContext.enforceCallingPermission(
                Manifest.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA, null);

        setDataDownloadState(DATA_DOWNLOAD_COMPLETE, userHandle.getIdentifier(), false /* force */);
        @InternalRestoreState
        int curDataRestoreState = getDataRestoreState(userHandle.getIdentifier());
        if (curDataRestoreState >= INTERNAL_RESTORE_STATE_STAGING_IN_PROGRESS) {
            if (curDataRestoreState >= INTERNAL_RESTORE_STATE_STAGING_DONE) {
                Slog.w(TAG, "Staging is already done. Cur state " + curDataRestoreState);
            } else {
                // Maybe the caller died and is trying to stage the data again.
                Slog.w(TAG, "Already in the process of staging.");
            }
            HealthConnectThreadScheduler.scheduleInternalTask(
                    () -> {
                        try {
                            callback.onResult();
                        } catch (RemoteException e) {
                            Log.e(TAG, "Restore response could not be sent to the caller.", e);
                        }
                    });
            return;
        }
        setDataRestoreState(
                INTERNAL_RESTORE_STATE_STAGING_IN_PROGRESS,
                userHandle.getIdentifier(),
                false /* force */);

        Map<String, ParcelFileDescriptor> origPfdsByFileName =
                stageRemoteDataRequest.getPfdsByFileName();
        Map<String, HealthConnectException> exceptionsByFileName =
                new ArrayMap<>(origPfdsByFileName.size());
        Map<String, ParcelFileDescriptor> pfdsByFileName =
                new ArrayMap<>(origPfdsByFileName.size());
        for (var entry : origPfdsByFileName.entrySet()) {
            try {
                pfdsByFileName.put(entry.getKey(), entry.getValue().dup());
            } catch (IOException e) {
                exceptionsByFileName.put(
                        entry.getKey(),
                        new HealthConnectException(
                                HealthConnectException.ERROR_IO, e.getMessage()));
            }
        }

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    File stagedRemoteDataDir =
                            getStagedRemoteDataDirectoryForUser(userHandle.getIdentifier());
                    try {
                        stagedRemoteDataDir.mkdirs();

                        // Now that we have the dir we can try to copy all the data.
                        // Any exceptions we face will be collected and shared with the caller.
                        pfdsByFileName.forEach(
                                (fileName, pfd) -> {
                                    File destination = new File(stagedRemoteDataDir, fileName);
                                    try (FileInputStream inputStream =
                                            new FileInputStream(pfd.getFileDescriptor())) {
                                        Path destinationPath =
                                                FileSystems.getDefault()
                                                        .getPath(destination.getAbsolutePath());
                                        Files.copy(
                                                inputStream,
                                                destinationPath,
                                                StandardCopyOption.REPLACE_EXISTING);
                                    } catch (IOException e) {
                                        destination.delete();
                                        exceptionsByFileName.put(
                                                fileName,
                                                new HealthConnectException(
                                                        HealthConnectException.ERROR_IO,
                                                        e.getMessage()));
                                    } catch (SecurityException e) {
                                        destination.delete();
                                        exceptionsByFileName.put(
                                                fileName,
                                                new HealthConnectException(
                                                        HealthConnectException.ERROR_SECURITY,
                                                        e.getMessage()));
                                    } finally {
                                        try {
                                            pfd.close();
                                        } catch (IOException e) {
                                            exceptionsByFileName.put(
                                                    fileName,
                                                    new HealthConnectException(
                                                            HealthConnectException.ERROR_IO,
                                                            e.getMessage()));
                                        }
                                    }
                                });
                    } finally {
                        // We are done staging all the remote data, update the data restore state.
                        // Even if we encountered any exception we still say that we are "done" as
                        // we don't expect the caller to retry and see different results.
                        setDataRestoreState(
                                INTERNAL_RESTORE_STATE_STAGING_DONE,
                                userHandle.getIdentifier(),
                                false /* force */);

                        // Share the result / exception with the caller.
                        try {
                            if (exceptionsByFileName.isEmpty()) {
                                callback.onResult();
                            } else {
                                setDataRestoreError(
                                        RESTORE_ERROR_FETCHING_DATA, userHandle.getIdentifier());
                                callback.onError(
                                        new StageRemoteDataException(exceptionsByFileName));
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG, "Restore response could not be sent to the caller.", e);
                        } catch (SecurityException e) {
                            Log.e(
                                    TAG,
                                    "Restore response could not be sent due to conflicting AIDL "
                                            + "definitions",
                                    e);
                        }
                    }
                });
    }

    /**
     * @see HealthConnectManager#deleteAllStagedRemoteData
     */
    @Override
    public void deleteAllStagedRemoteData(@NonNull UserHandle userHandle) {
        mContext.enforceCallingPermission(
                DELETE_STAGED_HEALTH_CONNECT_REMOTE_DATA_PERMISSION, null);
        deleteDir(getStagedRemoteDataDirectoryForUser(userHandle.getIdentifier()));
        setDataDownloadState(
                DATA_DOWNLOAD_STATE_UNKNOWN, userHandle.getIdentifier(), true /* force */);
        setDataRestoreState(
                INTERNAL_RESTORE_STATE_UNKNOWN, userHandle.getIdentifier(), true /* force */);
        setDataRestoreError(RESTORE_ERROR_NONE, userHandle.getIdentifier());
    }

    /**
     * @see HealthConnectManager#updateDataDownloadState
     */
    @Override
    public void updateDataDownloadState(
            @DataDownloadState int downloadState, @NonNull UserHandle userHandle) {
        mContext.enforceCallingPermission(
                Manifest.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA, null);
        setDataDownloadState(downloadState, userHandle.getIdentifier(), false /* force */);

        if (downloadState == DATA_DOWNLOAD_COMPLETE) {
            setDataRestoreState(
                    INTERNAL_RESTORE_STATE_WAITING_FOR_STAGING,
                    userHandle.getIdentifier(),
                    false /* force */);
        } else if (downloadState == DATA_DOWNLOAD_FAILED) {
            setDataRestoreState(
                    INTERNAL_RESTORE_STATE_MERGING_DONE,
                    userHandle.getIdentifier(),
                    false /* force */);
            setDataRestoreError(RESTORE_ERROR_FETCHING_DATA, userHandle.getIdentifier());
        }
    }

    /**
     * @see HealthConnectManager#getHealthConnectDataState
     */
    @Override
    public void getHealthConnectDataState(
            @NonNull UserHandle userHandle, @NonNull IGetHealthConnectDataStateCallback callback) {
        enforceAnyOfPermissions(
                MANAGE_HEALTH_DATA_PERMISSION, Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA);

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        @HealthConnectDataState.DataRestoreError
                        int dataRestoreError = getDataRestoreError(userHandle.getIdentifier());
                        @HealthConnectDataState.DataRestoreState
                        int dataRestoreState = RESTORE_STATE_IDLE;

                        @InternalRestoreState
                        int currentRestoreState = getDataRestoreState(userHandle.getIdentifier());

                        if (currentRestoreState == INTERNAL_RESTORE_STATE_MERGING_DONE) {
                            // already with correct values.
                        } else if (currentRestoreState
                                == INTERNAL_RESTORE_STATE_MERGING_IN_PROGRESS) {
                            dataRestoreState = RESTORE_STATE_IN_PROGRESS;
                        } else if (currentRestoreState != INTERNAL_RESTORE_STATE_UNKNOWN) {
                            dataRestoreState = RESTORE_STATE_PENDING;
                        }

                        @DataDownloadState
                        int currentDownloadState = getDataDownloadState(userHandle.getIdentifier());
                        if (currentDownloadState == DATA_DOWNLOAD_FAILED) {
                            // already with correct values.
                        } else if (currentDownloadState != DATA_DOWNLOAD_STATE_UNKNOWN) {
                            dataRestoreState = RESTORE_STATE_PENDING;
                        }

                        try {
                            callback.onResult(
                                    new HealthConnectDataState(
                                            dataRestoreState,
                                            dataRestoreError,
                                            MIGRATION_STATE_IDLE));
                        } catch (RemoteException remoteException) {
                            Log.e(
                                    TAG,
                                    "HealthConnectDataState could not be sent to the caller.",
                                    remoteException);
                        }
                    } catch (RuntimeException e) {
                        // exception getting the state from the disk
                        try {
                            callback.onError(
                                    new HealthConnectExceptionParcel(
                                            new HealthConnectException(
                                                    HealthConnectException.ERROR_IO,
                                                    e.getMessage())));
                        } catch (RemoteException remoteException) {
                            Log.e(
                                    TAG,
                                    "Exception for getHealthConnectDataState could not be sent to"
                                            + " the caller.",
                                    remoteException);
                        }
                    }
                });
    }

    @VisibleForTesting
    Set<String> getStagedRemoteFileNames(int userId) {
        return Stream.of(getStagedRemoteDataDirectoryForUser(userId).listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .collect(Collectors.toSet());
    }

    void setDataRestoreState(
            @InternalRestoreState int dataRestoreState, int userID, boolean force) {
        @InternalRestoreState int currentRestoreState = getDataRestoreState(userID);
        if (!force && currentRestoreState >= dataRestoreState) {
            Slog.w(
                    TAG,
                    "Attempt to update data restore state in wrong order from "
                            + currentRestoreState
                            + " to "
                            + dataRestoreState);
            return;
        }
        // TODO(b/264070899) Store on a per user basis when we have per user db
        PreferenceHelper.getInstance()
                .insertPreference(DATA_RESTORE_STATE_KEY, String.valueOf(dataRestoreState));
    }

    @InternalRestoreState
    int getDataRestoreState(int userId) {
        // TODO(b/264070899) Get on a per user basis when we have per user db
        String restoreStateOnDisk =
                PreferenceHelper.getInstance().getPreference(DATA_RESTORE_STATE_KEY);
        @InternalRestoreState int currentRestoreState = INTERNAL_RESTORE_STATE_UNKNOWN;
        if (restoreStateOnDisk == null) {
            return currentRestoreState;
        }
        try {
            currentRestoreState = Integer.parseInt(restoreStateOnDisk);
        } catch (Exception e) {
            Slog.e(TAG, "Exception parsing restoreStateOnDisk: " + restoreStateOnDisk, e);
        }
        return currentRestoreState;
    }

    @DataDownloadState
    private int getDataDownloadState(int userId) {
        // TODO(b/264070899) Get on a per user basis when we have per user db
        String downloadStateOnDisk =
                PreferenceHelper.getInstance().getPreference(DATA_DOWNLOAD_STATE_KEY);
        @DataDownloadState int currentDownloadState = DATA_DOWNLOAD_STATE_UNKNOWN;
        if (downloadStateOnDisk == null) {
            return currentDownloadState;
        }
        try {
            currentDownloadState = Integer.parseInt(downloadStateOnDisk);
        } catch (Exception e) {
            Slog.e(TAG, "Exception parsing downloadStateOnDisk " + downloadStateOnDisk, e);
        }
        return currentDownloadState;
    }

    private void setDataDownloadState(
            @DataDownloadState int downloadState, int userId, boolean force) {
        @DataDownloadState int currentDownloadState = getDataDownloadState(userId);
        if (!force
                && (currentDownloadState == DATA_DOWNLOAD_FAILED
                        || currentDownloadState == DATA_DOWNLOAD_COMPLETE)) {
            Slog.w(TAG, "HC data download already in terminal state.");
            return;
        }
        // TODO(b/264070899) Store on a per user basis when we have per user db
        PreferenceHelper.getInstance()
                .insertPreference(DATA_DOWNLOAD_STATE_KEY, String.valueOf(downloadState));
    }

    // Creating a separate single line method to keep this code close to the rest of the code that
    // uses PreferenceHelper to keep data on the disk.
    private void setDataRestoreError(
            @HealthConnectDataState.DataRestoreError int dataRestoreError, int userId) {
        // TODO(b/264070899) Store on a per user basis when we have per user db
        PreferenceHelper.getInstance()
                .insertPreference(DATA_RESTORE_ERROR_KEY, String.valueOf(dataRestoreError));
    }

    private @HealthConnectDataState.DataRestoreError int getDataRestoreError(int userId) {
        // TODO(b/264070899) Get on a per user basis when we have per user db
        @HealthConnectDataState.DataRestoreError int dataRestoreError = RESTORE_ERROR_NONE;
        String restoreErrorOnDisk =
                PreferenceHelper.getInstance().getPreference(DATA_RESTORE_ERROR_KEY);
        try {
            dataRestoreError = Integer.parseInt(restoreErrorOnDisk);
        } catch (Exception e) {
            Slog.e(TAG, "Exception parsing restoreErrorOnDisk " + restoreErrorOnDisk, e);
        }
        return dataRestoreError;
    }

    @NonNull
    private DataMigrationManager getDataMigrationManager(@NonNull UserHandle userHandle) {
        final Context userContext = mContext.createContextAsUser(userHandle, 0);

        return new DataMigrationManager(
                userContext,
                mTransactionManager,
                mPermissionHelper,
                mFirstGrantTimeManager,
                DeviceInfoHelper.getInstance(),
                AppInfoHelper.getInstance(),
                RecordHelperProvider.getInstance());
    }

    private Map<Integer, List<DataOrigin>> getPopulatedRecordTypeInfoResponses() {
        Map<Integer, Class<? extends Record>> recordIdToExternalRecordClassMap =
                RecordMapper.getInstance().getRecordIdToExternalRecordClassMap();
        Map<Integer, List<DataOrigin>> recordTypeInfoResponses =
                new ArrayMap<>(recordIdToExternalRecordClassMap.size());
        recordIdToExternalRecordClassMap
                .keySet()
                .forEach(
                        (recordType) -> {
                            RecordHelper<?> recordHelper =
                                    RecordHelperProvider.getInstance().getRecordHelper(recordType);
                            Objects.requireNonNull(recordHelper);
                            List<DataOrigin> packages =
                                    mTransactionManager.getDistinctPackageNamesForRecordTable(
                                            recordHelper);
                            recordTypeInfoResponses.put(recordType, packages);
                        });
        return recordTypeInfoResponses;
    }

    private void enforceAnyOfPermissions(@NonNull String... permissions) {
        for (var permission : permissions) {
            if (mContext.checkCallingPermission(permission) == PERMISSION_GRANTED) {
                return;
            }
        }
        throw new SecurityException(
                "Caller requires one of the following permissions: "
                        + String.join(", ", permissions));
    }

    private void enforceRecordWritePermissionForRecords(
            List<RecordInternal<?>> recordInternals, int uid) {
        Set<Integer> recordTypeIdsToEnforce = new ArraySet<>();
        for (RecordInternal<?> recordInternal : recordInternals) {
            recordTypeIdsToEnforce.add(recordInternal.getRecordType());
        }

        enforceRecordWritePermissionInternal(recordTypeIdsToEnforce.stream().toList(), uid);
    }

    private boolean hasDataManagementPermission(int uid, int pid) {
        return mContext.checkPermission(MANAGE_HEALTH_DATA_PERMISSION, pid, uid)
                == PERMISSION_GRANTED;
    }

    private void enforceRecordWritePermission(List<Integer> recordTypeIds, int uid) {
        enforceRecordWritePermissionInternal(recordTypeIds, uid);
    }

    private void enforceRecordReadPermission(List<Integer> recordTypeIds, int uid) {
        for (Integer recordTypeId : recordTypeIds) {
            String permissionName =
                    HealthPermissions.getHealthReadPermission(
                            RecordTypePermissionCategoryMapper
                                    .getHealthPermissionCategoryForRecordType(recordTypeId));
            if (mPermissionManager.checkPermissionForStartDataDelivery(
                            permissionName, new AttributionSource.Builder(uid).build(), null)
                    != PERMISSION_GRANTED) {
                throw new SecurityException(
                        "Caller doesn't have "
                                + permissionName
                                + " to read record of type "
                                + RecordMapper.getInstance()
                                        .getRecordIdToExternalRecordClassMap()
                                        .get(recordTypeId));
            }
        }
    }

    /**
     * Returns a pair of boolean values. where the first value specifies enforceSelfRead, i.e., the
     * app is allowed to read self data, and the second boolean value is true if the caller has
     * MANAGE_HEALTH_DATA_PERMISSION, which signifies that the caller is UI.
     */
    private boolean enforceRecordReadPermission(int uid, int recordTypeId) {
        boolean enforceSelfRead = false;
        try {
            enforceRecordReadPermission(Collections.singletonList(recordTypeId), uid);
        } catch (SecurityException readSecurityException) {
            try {
                enforceRecordWritePermission(Collections.singletonList(recordTypeId), uid);
                // Apps are always allowed to read self data if they have insert
                // permission.
                enforceSelfRead = true;
            } catch (SecurityException writeSecurityException) {
                throw readSecurityException;
            }
        }
        return enforceSelfRead;
    }

    private void enforceRecordWritePermissionInternal(List<Integer> recordTypeIds, int uid) {
        for (Integer recordTypeId : recordTypeIds) {
            String permissionName =
                    HealthPermissions.getHealthWritePermission(
                            RecordTypePermissionCategoryMapper
                                    .getHealthPermissionCategoryForRecordType(recordTypeId));

            if (mPermissionManager.checkPermissionForStartDataDelivery(
                            permissionName, new AttributionSource.Builder(uid).build(), null)
                    != PERMISSION_GRANTED) {
                throw new SecurityException(
                        "Caller doesn't have "
                                + permissionName
                                + " to write to record type "
                                + RecordMapper.getInstance()
                                        .getRecordIdToExternalRecordClassMap()
                                        .get(recordTypeId));
            }
        }
    }

    private void finishDataDeliveryRead(int recordTypeId, int uid) {
        finishDataDeliveryRead(Collections.singletonList(recordTypeId), uid);
    }

    private void finishDataDeliveryRead(List<Integer> recordTypeIds, int uid) {
        try {
            for (Integer recordTypeId : recordTypeIds) {
                String permissionName =
                        HealthPermissions.getHealthReadPermission(
                                RecordTypePermissionCategoryMapper
                                        .getHealthPermissionCategoryForRecordType(recordTypeId));
                mPermissionManager.finishDataDelivery(
                        permissionName, new AttributionSource.Builder(uid).build());
            }
        } catch (Exception exception) {
            // Ignore: HC API has already fulfilled the result, ignore any exception we hit here
        }
    }

    private void finishDataDeliveryWriteRecords(List<RecordInternal<?>> recordInternals, int uid) {
        Set<Integer> recordTypeIdsToEnforce = new ArraySet<>();
        for (RecordInternal<?> recordInternal : recordInternals) {
            recordTypeIdsToEnforce.add(recordInternal.getRecordType());
        }

        finishDataDeliveryWrite(recordTypeIdsToEnforce.stream().toList(), uid);
    }

    private void finishDataDeliveryWrite(List<Integer> recordTypeIds, int uid) {
        try {
            for (Integer recordTypeId : recordTypeIds) {
                String permissionName =
                        HealthPermissions.getHealthWritePermission(
                                RecordTypePermissionCategoryMapper
                                        .getHealthPermissionCategoryForRecordType(recordTypeId));
                mPermissionManager.finishDataDelivery(
                        permissionName, new AttributionSource.Builder(uid).build());
            }
        } catch (Exception exception) {
            // Ignore: HC API has already fulfilled the result, ignore any exception we hit here
        }
    }

    // TODO(b/264794517) Refactor pure util methods out into a separate class
    private static File getDataSystemCeHCDirectoryForUser(int userId) {
        // Duplicates the implementation of Environment#getDataSystemCeDirectory
        // TODO(b/191059409): Unhide Environment#getDataSystemCeDirectory and switch to it.
        File systemCeDir = new File(Environment.getDataDirectory(), "system_ce");
        File systemCeUserDir = new File(systemCeDir, String.valueOf(userId));
        return new File(systemCeUserDir, "healthconnect");
    }

    // TODO(b/264794517) Refactor pure util methods out into a separate class
    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    // TODO(b/264794517) Refactor pure util methods out into a separate class
    private static File getStagedRemoteDataDirectoryForUser(int userId) {
        File hcDirectoryForUser = getDataSystemCeHCDirectoryForUser(userId);
        return new File(hcDirectoryForUser, "remote_staged");
    }

    private static void tryAndThrowException(
            @NonNull IInsertRecordsResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IAggregateRecordsResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IReadRecordsResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IActivityDatesResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IGetChangeLogTokenCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IAccessLogsResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.toString())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IEmptyResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.toString())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IApplicationInfoResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IChangeLogsResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.toString())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IRecordTypeInfoResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.getMessage())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IGetPriorityResponseCallback callback,
            @NonNull Exception exception,
            @HealthConnectException.ErrorCode int errorCode) {
        try {
            callback.onError(
                    new HealthConnectExceptionParcel(
                            new HealthConnectException(errorCode, exception.toString())));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    private static void tryAndThrowException(
            @NonNull IMigrationCallback callback,
            @NonNull Exception exception,
            @MigrationException.ErrorCode int errorCode,
            @Nullable String failedEntityId) {
        try {
            callback.onError(
                    new MigrationException(errorCode, exception.toString(), failedEntityId));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }
}
