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

package android.healthconnect.cts.logging

import android.cts.statsdatom.lib.AtomTestUtils
import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import android.healthfitness.ui.ElementId
import android.healthfitness.ui.PageId
import com.android.os.healthfitness.ui.UiExtensionAtoms
import com.android.tradefed.build.IBuildInfo
import com.android.tradefed.testtype.DeviceTestCase
import com.android.tradefed.testtype.IBuildReceiver
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ExtensionRegistry

class HealthConnectUiLogsTests : DeviceTestCase(), IBuildReceiver {

    companion object {
        private const val TAG = "HomeFragmentHostTest"
        private const val TEST_APP_PKG_NAME = "android.healthconnect.cts.testhelper"
    }

    private lateinit var mCtsBuild: IBuildInfo
    private lateinit var packageName: String

    override fun setUp() {
        super.setUp()
        assertThat(mCtsBuild).isNotNull()
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        val pmResult =
            device.executeShellCommand(
                "pm list packages com.google.android.healthconnect.controller")
        packageName =
            if (pmResult.isEmpty()) {
                "com.android.healthconnect.controller"
            } else {
                "com.google.android.healthconnect.controller"
            }

        ConfigUtils.createConfigBuilder(packageName)
        ConfigUtils.uploadConfigForPushedAtoms(
            device,
            packageName,
            intArrayOf(
                UiExtensionAtoms.HEALTH_CONNECT_UI_IMPRESSION_FIELD_NUMBER,
                UiExtensionAtoms.HEALTH_CONNECT_UI_INTERACTION_FIELD_NUMBER))
    }

    @Throws(Exception::class)
    override fun tearDown() {
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
        super.tearDown()
    }

    override fun setBuild(buildInfo: IBuildInfo) {
        mCtsBuild = buildInfo
    }

    fun testHomeScreenImpressions() {
        val pageId = PageId.HOME_PAGE
        DeviceUtils.runDeviceTests(
            device, TEST_APP_PKG_NAME, ".HealthConnectUiTestHelper", "openHomeFragment")
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())
        val registry = ExtensionRegistry.newInstance()
        UiExtensionAtoms.registerAllExtensions(registry)

        val data = ReportUtils.getEventMetricDataList(device, registry)
        assertThat(data.size).isAtLeast(2)

        // Page impression
        val pageImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    !it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).hasElement()
            }
        assertThat(pageImpression.size).isEqualTo(1)

        val appPermissionsImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.APP_PERMISSIONS_BUTTON
            }
        assertThat(appPermissionsImpression.size).isEqualTo(1)

        val dataAndAccessImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.DATA_AND_ACCESS_BUTTON
            }
        assertThat(dataAndAccessImpression.size).isEqualTo(1)

        val recentAccessDataImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.RECENT_ACCESS_ENTRY
            }
        assertThat(recentAccessDataImpression.size).isAtLeast(1)

        val seeAllRecentAccessImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.SEE_ALL_RECENT_ACCESS_BUTTON
            }
        assertThat(seeAllRecentAccessImpression.size).isEqualTo(1)

        val toolbarImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.TOOLBAR_SETTINGS_BUTTON
            }
        assertThat(toolbarImpression.size).isEqualTo(1)
    }

    fun testRecentAccessImpressions() {
        val pageId = PageId.RECENT_ACCESS_PAGE
        DeviceUtils.runDeviceTests(
            device, TEST_APP_PKG_NAME, ".HealthConnectUiTestHelper", "openRecentAccess")
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())
        val registry = ExtensionRegistry.newInstance()
        UiExtensionAtoms.registerAllExtensions(registry)
        val data = ReportUtils.getEventMetricDataList(device, registry)

        assertThat(data.size).isAtLeast(2)

        // Page impression
        val pageImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    !it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).hasElement()
            }
        assertThat(pageImpression.size).isEqualTo(1)

        val recentAppImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.RECENT_ACCESS_ENTRY
            }
        assertThat(recentAppImpression.size).isAtLeast(1)

        val managePermissionsButtonImpression =
            data.filter {
                it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                        ElementId.MANAGE_PERMISSIONS_FLOATING_BUTTON
            }
        assertThat(managePermissionsButtonImpression.size).isEqualTo(1)

        val toolbarImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                            ElementId.TOOLBAR_SETTINGS_BUTTON
                }
        assertThat(toolbarImpression.size).isEqualTo(1)
    }

    fun testDataAndAccessImpressions() {
        val pageId = PageId.CATEGORIES_PAGE
        DeviceUtils.runDeviceTests(
                device, TEST_APP_PKG_NAME, ".HealthConnectUiTestHelper", "openDataAndAccess")
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())
        val registry = ExtensionRegistry.newInstance()
        UiExtensionAtoms.registerAllExtensions(registry)
        val data = ReportUtils.getEventMetricDataList(device, registry)

        assertThat(data.size).isAtLeast(2)

        // Page impression
        val pageImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            !it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).hasElement()
                }
        assertThat(pageImpression.size).isEqualTo(1)

        val categoryImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                            ElementId.CATEGORY_BUTTON
                }
        assertThat(categoryImpression.size).isEqualTo(2)

        val seeAllCategoriesImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                            ElementId.SEE_ALL_CATEGORIES_BUTTON
                }
        assertThat(seeAllCategoriesImpression.size).isEqualTo(1)

        val autoDeleteImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                            ElementId.AUTO_DELETE_BUTTON
                }
        assertThat(autoDeleteImpression.size).isEqualTo(1)

        val deleteAllDataImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                            ElementId.DELETE_ALL_DATA_BUTTON
                }
        assertThat(deleteAllDataImpression.size).isEqualTo(1)


        val toolbarImpression =
                data.filter {
                    it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).page == pageId &&
                            it.atom.getExtension(UiExtensionAtoms.healthConnectUiImpression).element ==
                            ElementId.TOOLBAR_SETTINGS_BUTTON
                }
        assertThat(toolbarImpression.size).isEqualTo(1)
    }
}