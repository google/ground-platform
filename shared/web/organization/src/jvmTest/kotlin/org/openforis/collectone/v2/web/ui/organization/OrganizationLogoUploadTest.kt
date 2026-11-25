/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.web.ui.organization

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import org.openforis.collectone.v2.app.domain.model.MembershipStatus
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationMember
import org.openforis.collectone.v2.app.domain.model.OrganizationRole
import org.openforis.collectone.v2.app.domain.usecase.ResolvedLibrary
import org.openforis.collectone.v2.web.ui.common.ImageFilePickResult
import org.openforis.collectone.v2.web.ui.common.OrganizationAvatar

@OptIn(ExperimentalTestApi::class)
class OrganizationLogoUploadTest {
  @Test
  fun organizationAvatar_ignoresLegacyPlaceholderScheme_andShowsInitialsBadge() =
    runDesktopComposeUiTest {
      setContent {
        OrganizationAvatar(name = "Kenya Forest Service", logoUrl = "avatar:2")
      }
      waitForIdle()

      onNodeWithText("KS").assertExists()
    }

  @Test
  fun organizationAvatar_withValidDataUri_rendersLogoInsteadOfInitials() = runDesktopComposeUiTest {
    setContent {
      OrganizationAvatar(name = "Kenya Forest Service", logoUrl = samplePngDataUrl())
    }
    waitForIdle()

    assertEquals(0, onAllNodesWithText("KS").fetchSemanticsNodes().size)
  }

  @Test
  fun manager_canUploadSaveAndRemoveOrganizationLogo() = runDesktopComposeUiTest {
    val org =
      Organization(
        id = "org-1",
        name = "Kenya Forest Service",
        members =
          listOf(
            OrganizationMember(
              email = "manager@example.org",
              role = OrganizationRole.MANAGER,
              status = MembershipStatus.ACTIVE,
            )
          ),
      )
    val uploadedLogo = samplePngDataUrl()
    var savedLogoUrl: String? = "unset"
    val actions =
      RecordingOrganizationActions(onUpdateDetails = { logoUrl -> savedLogoUrl = logoUrl })
    val uiState =
      OrganizationUiState(
        organizations = listOf(org),
        isSignedIn = true,
        signedInUserEmail = "manager@example.org",
        openOrganizationId = org.id,
      )

    setContent {
      OrganizationPage(
        uiState = uiState,
        actions = actions,
        sidePanelWidthDp = 260f,
        onSidePanelWidthChange = {},
        onOpenSurvey = {},
        onCreateSurvey = {},
        surveyLibrary = { ResolvedLibrary(organizationId = it) },
        header = {},
        imageFilePicker = { _, onResult ->
          onResult(ImageFilePickResult.Picked("kfs.png", uploadedLogo))
        },
      )
    }
    waitForIdle()

    onNodeWithText("Save").assertIsNotEnabled()
    onNodeWithText("Upload logo").performClick()
    waitForIdle()

    onNodeWithText("Change logo").assertExists()
    onNodeWithText("Remove").assertExists()
    onNodeWithText("Save").assertIsEnabled()

    onNodeWithText("Save").performScrollTo().performClick()
    waitForIdle()
    assertEquals(uploadedLogo, savedLogoUrl)

    onNodeWithText("Remove").performScrollTo().performClick()
    waitForIdle()
    onNodeWithText("Upload logo").assertExists()
    onNodeWithText("Save").assertIsNotEnabled()
  }

  @Test
  fun nonManager_doesNotSeeLogoUploadControls() = runDesktopComposeUiTest {
    val org =
      Organization(
        id = "org-1",
        name = "Kenya Forest Service",
        members =
          listOf(
            OrganizationMember(
              email = "member@example.org",
              role = OrganizationRole.MEMBER,
              status = MembershipStatus.ACTIVE,
            )
          ),
      )
    val uiState =
      OrganizationUiState(
        organizations = listOf(org),
        isSignedIn = true,
        signedInUserEmail = "member@example.org",
        openOrganizationId = org.id,
      )

    setContent {
      OrganizationPage(
        uiState = uiState,
        actions = RecordingOrganizationActions(),
        sidePanelWidthDp = 260f,
        onSidePanelWidthChange = {},
        onOpenSurvey = {},
        onCreateSurvey = {},
        surveyLibrary = { ResolvedLibrary(organizationId = it) },
        header = {},
      )
    }
    waitForIdle()

    assertEquals(0, onAllNodesWithText("Upload logo").fetchSemanticsNodes().size)
    assertEquals(0, onAllNodesWithText("Change logo").fetchSemanticsNodes().size)
  }

  private fun samplePngDataUrl(): String {
    val bytes =
      ByteArrayOutputStream().use { out ->
        ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", out)
        out.toByteArray()
      }
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
  }

  private class RecordingOrganizationActions(
    private val onUpdateDetails: (logoUrl: String?) -> Unit = {}
  ) : OrganizationActions {
    override fun openOrganization(organizationId: String) = Unit

    override fun openAdminPanel() = Unit

    override fun openOrganizations() = Unit

    override fun dismissNotice() = Unit

    override fun createOrganization(
      name: String,
      description: String,
      isListed: Boolean,
      organizationType: org.openforis.collectone.v2.app.domain.model.OrganizationType?,
      countryCode: String?,
    ): String = ""

    override fun updateOrganizationDetails(
      organizationId: String,
      name: String,
      description: String,
      websiteUrl: String,
      isListed: Boolean,
      organizationType: org.openforis.collectone.v2.app.domain.model.OrganizationType?,
      countryCode: String?,
      logoUrl: String?,
    ) {
      onUpdateDetails(logoUrl)
    }

    override fun setExcludeFromPlatformAggregates(organizationId: String, exclude: Boolean) = Unit

    override fun updateOrganizationQuotaLimits(
      organizationId: String,
      limits: org.openforis.collectone.v2.app.domain.model.QuotaLimitsConfig,
    ) = Unit

    override fun deleteOrganization(organizationId: String) = Unit

    override fun inviteMember(
      organizationId: String,
      email: String,
      role: OrganizationRole,
    ) = null

    override fun resetInviteLink(organizationId: String, email: String) = Unit

    override fun acceptInvite(
      organizationId: String,
      email: String,
      displayName: String,
      photoUrl: String?,
    ) = null

    override fun requestToJoin(organizationId: String) = Unit

    override fun approveRequest(organizationId: String, email: String) = Unit

    override fun setMemberRole(organizationId: String, email: String, role: OrganizationRole) = Unit

    override fun removeMember(organizationId: String, email: String) = Unit

    override fun addImagerySource(
      organizationId: String,
      name: String,
      urlTemplate: String,
      type: org.openforis.collectone.v2.app.domain.model.ImagerySourceType,
      allowOfflineDownload: Boolean,
      expression: google.earthengine.v1main.Expression?,
    ) = null

    override fun updateImagerySource(
      organizationId: String,
      sourceId: String,
      name: String,
      urlTemplate: String,
      allowOfflineDownload: Boolean,
    ) = null

    override fun setImagerySourceOfflineAllowed(
      organizationId: String,
      sourceId: String,
      allowOfflineDownload: Boolean,
    ) = Unit

    override fun removeImagerySource(organizationId: String, sourceId: String) = Unit

    override fun setDictionaryQuery(query: String) = Unit

    override fun saveConcept(
      concept: org.openforis.collectone.v2.app.domain.model.LibraryConcept,
      isNew: Boolean,
    ) = null

    override fun deleteConcept(organizationId: String, conceptId: String) = null

    override fun renameTemplate(
      organizationId: String,
      templateId: String,
      title: String,
      description: String,
    ) = null

    override fun deleteTemplate(organizationId: String, templateId: String) = Unit

    override fun renamePurposePack(
      organizationId: String,
      packId: String,
      title: String,
      description: String,
    ) = null

    override fun deletePurposePack(organizationId: String, packId: String) = Unit

    override fun setGlobalEntryHidden(
      organizationId: String,
      globalEntryId: String,
      hidden: Boolean,
    ) = Unit
  }
}
