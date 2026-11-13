package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaperSerializationTest {

  @Test
  fun sectionsRoundTrip_preservesQuestionsOptionsAndAnswers() {
    val original = listOf(
      PaperSection(
        heading = "Section A: MCQs",
        marksLabel = "15 Marks",
        questions = listOf(
          PaperQuestion(
            number = 1L,
            text = "SI unit of force?",
            marks = 1L,
            options = listOf("Newton", "Joule", "Watt", "Pascal"),
            answer = "Newton",
          )
        ),
      )
    )
    val json = PaperSerialization.sectionsToJson(original)
    val restored = PaperSerialization.sectionsFromJson(json)
    assertEquals(original, restored)
  }

  @Test
  fun contactsRoundTrip_preservesLabelsNumbersAndFlags() {
    val original = listOf(
      HelpContact(
        label = "Sir Helpline",
        phoneNumber = "+923001234567",
        allowCall = true,
        allowWhatsApp = true,
      )
    )
    val json = PaperSerialization.contactsToJson(original)
    val restored = PaperSerialization.contactsFromJson(json)
    assertEquals(original, restored)
  }
}
