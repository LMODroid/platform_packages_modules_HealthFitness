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

package com.android.server.healthconnect.storage.request;

import android.annotation.NonNull;
import android.healthconnect.Constants;
import android.util.Pair;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Creates a request and the table create statements for it.
 *
 * <p>Note: For every child table. This class automatically creates index statements for all the
 * defined foreign keys.
 *
 * @hide
 */
public final class CreateTableRequest {
    public static final String TAG = "HealthConnectCreate";
    public static final String FOREIGN_KEY_COMMAND = " FOREIGN KEY (";
    public static final String DELIMITER_COLUMN_LIST = ",";
    private static final String CREATE_INDEX_COMMAND = "CREATE INDEX  idx_";
    private static final String CREATE_TABLE_COMMAND = "CREATE TABLE IF NOT EXISTS ";
    private final String mTableName;
    private final List<Pair<String, String>> mColumnInfo;

    private List<ForeignKey> mForeignKeys;
    private List<CreateTableRequest> mChildTableRequests = Collections.emptyList();

    public CreateTableRequest(String tableName, List<Pair<String, String>> columnInfo) {
        mTableName = tableName;
        mColumnInfo = columnInfo;
    }

    @NonNull
    public CreateTableRequest addForeignKey(
            String referencedTable, List<String> columnNames, List<String> referencedColumnNames) {
        mForeignKeys = mForeignKeys == null ? new ArrayList<>() : mForeignKeys;
        mForeignKeys.add(new ForeignKey(referencedTable, columnNames, referencedColumnNames));

        return this;
    }

    @NonNull
    public List<CreateTableRequest> getChildTableRequests() {
        return mChildTableRequests;
    }

    public CreateTableRequest setChildTableRequests(
            @NonNull List<CreateTableRequest> childCreateTableRequests) {
        Objects.requireNonNull(childCreateTableRequests);

        mChildTableRequests = childCreateTableRequests;

        return this;
    }

    @NonNull
    public String getCreateCommand() {
        final StringBuilder builder = new StringBuilder(CREATE_TABLE_COMMAND);
        builder.append(mTableName);
        builder.append(" (");
        mColumnInfo.forEach(
                (columnInfo) ->
                        builder.append(columnInfo.first)
                                .append(" ")
                                .append(columnInfo.second)
                                .append(", "));

        if (mForeignKeys != null) {
            for (ForeignKey foreignKey : mForeignKeys) {
                builder.append(foreignKey.getFkConstraint()).append(", ");
            }
        }

        builder.setLength(builder.length() - 2); // Remove the last 2 char i.e. ", "
        builder.append(")");
        if (Constants.DEBUG) {
            Slog.d(TAG, "Create table: " + builder);
        }

        return builder.toString();
    }

    @NonNull
    public List<String> getCreateIndexStatements() {
        if (mForeignKeys != null) {
            List<String> result = new ArrayList<>(mForeignKeys.size());
            int index = 0;
            for (ForeignKey foreignKey : mForeignKeys) {
                result.add(foreignKey.getFkIndexStatement(index++));
            }

            return result;
        }

        return Collections.emptyList();
    }

    private final class ForeignKey {
        private final List<String> mColumnNames;
        private final String mReferencedTableName;
        private final List<String> mReferencedColumnNames;

        ForeignKey(
                String referencedTable,
                List<String> columnNames,
                List<String> referencedColumnNames) {
            mReferencedTableName = referencedTable;
            mColumnNames = columnNames;
            mReferencedColumnNames = referencedColumnNames;
        }

        String getFkConstraint() {
            return FOREIGN_KEY_COMMAND
                    + String.join(DELIMITER_COLUMN_LIST, mColumnNames)
                    + ")"
                    + " REFERENCES "
                    + mReferencedTableName
                    + "("
                    + String.join(DELIMITER_COLUMN_LIST, mReferencedColumnNames)
                    + ")";
        }

        String getFkIndexStatement(int fkNumber) {
            return CREATE_INDEX_COMMAND
                    + mTableName
                    + "_"
                    + fkNumber
                    + " ON "
                    + mTableName
                    + "("
                    + String.join(DELIMITER_COLUMN_LIST, mColumnNames)
                    + ")";
        }
    }
}
