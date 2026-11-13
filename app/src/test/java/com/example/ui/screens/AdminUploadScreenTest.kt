package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.TargetPapersTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdminUploadScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun adminEmailAuthorizationCheck_allowsOnlyVerifiedBootstrappedAdmins() {
    assertTrue(isAdminEmailAuthorized("vden4060@gmail.com", true))
    assertTrue(isAdminEmailAuthorized("karachieduofficial@gmail.com", true))
    assertTrue(isAdminEmailAuthorized("kbir94720@gmail.com", true))
    assertTrue(isAdminEmailAuthorized("JOKER94720@GMAIL.COM", true))

    assertFalse(isAdminEmailAuthorized("vden4060@gmail.com", false))
    assertFalse(isAdminEmailAuthorized("student@gmail.com", true))
    assertFalse(isAdminEmailAuthorized(null, true))
  }

  @Test
  fun adminUploadScreen_blocksUnauthorizedEmailAndShowsRestrictedState() {
    composeTestRule.setContent {
      TargetPapersTheme {
        AdminUploadScreen(
          userEmail = "student@example.com",
          isEmailVerified = true,
          isSaving = false,
          onBack = {},
          onUploadPaper = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("admin_upload_access_denied").assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_upload_pick_pdf_button").assertDoesNotExist()
  }

  @Test
  fun adminUploadScreen_showsPdfPickerAndDescriptionInputForVerifiedAdmin() {
    composeTestRule.setContent {
      TargetPapersTheme {
        AdminUploadScreen(
          userEmail = "joker94720@gmail.com",
          isEmailVerified = true,
          isSaving = false,
          onBack = {},
          onUploadPaper = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("admin_upload_verified_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_upload_pick_pdf_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_upload_description_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_upload_submit_button").performScrollTo().assertIsDisplayed()
  }
}
