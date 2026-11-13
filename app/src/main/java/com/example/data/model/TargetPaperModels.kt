package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

@IgnoreExtraProperties
data class PaperQuestion(
  val number: Long = 1L,
  val text: String = "",
  val marks: Long = 0L,
  val options: List<String> = emptyList(),
  val answer: String = "",
)

@IgnoreExtraProperties
data class PaperSection(
  val heading: String = "",
  val marksLabel: String = "",
  val questions: List<PaperQuestion> = emptyList(),
)

@IgnoreExtraProperties
data class TargetPaper(
  @DocumentId val id: String = "",
  val title: String = "",
  val subject: String = "",
  val classLevel: String = "",
  val examTerm: String = "",
  val totalMarks: Long = 0L,
  val durationText: String = "",
  val instructions: String = "",
  val externalPdfUrl: String = "",
  val pdfFileName: String = "",
  val pdfSizeBytes: Long = 0L,
  val pdfPageCount: Long = 0L,
  val pdfBase64: String = "",
  val sections: List<PaperSection> = emptyList(),
  val status: String = STATUS_PUBLISHED,
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val totalQuestionCount: Int
    get() = sections.sumOf { it.questions.size }

  val hasEmbeddedPdf: Boolean
    get() = pdfBase64.isNotBlank()

  val isPublished: Boolean
    get() = status == STATUS_PUBLISHED

  companion object {
    const val STATUS_PUBLISHED = "published"
    const val STATUS_DRAFT = "draft"
  }
}

@IgnoreExtraProperties
data class HelpContact(
  val label: String = "",
  val phoneNumber: String = "",
  val allowCall: Boolean = true,
  val allowWhatsApp: Boolean = true,
)

@IgnoreExtraProperties
data class SupportConfig(
  @DocumentId val id: String = "",
  val isPublic: Boolean = true,
  val whatsappChannelName: String = "",
  val whatsappChannelUrl: String = "",
  val sscGroupName: String = "",
  val sscGroupUrl: String = "",
  val hscGroupName: String = "",
  val hscGroupUrl: String = "",
  val supportNote: String = "",
  val helpContacts: List<HelpContact> = emptyList(),
  val updatedByUid: String = "",
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

object JokerOfficialDefaults {
  const val CHANNEL_NAME = "JOKER OFFICIAL Channel"
  const val CHANNEL_URL = "https://whatsapp.com/channel/0029VaZnOTr5vKA6nXxuWw3U"
  const val SSC_GROUP_NAME = "SSC (IX/X) Session"
  const val SSC_GROUP_URL = "https://chat.whatsapp.com/C4IFHnqFt7dEojcnvGYHUK"
  const val HSC_GROUP_NAME = "HSC (XI/XII) Session"
  const val HSC_GROUP_URL = "https://chat.whatsapp.com/KBZIZeWc2VXJoX8lY0bzCW"
  const val SUPPORT_NOTE = "Join the Joker Official WhatsApp Channel & SSC/HSC Session groups, or tap a helpline number below to call or chat on WhatsApp."

  fun defaultSupportConfig(): SupportConfig = SupportConfig(
    id = "support_settings",
    isPublic = true,
    whatsappChannelName = CHANNEL_NAME,
    whatsappChannelUrl = CHANNEL_URL,
    sscGroupName = SSC_GROUP_NAME,
    sscGroupUrl = SSC_GROUP_URL,
    hscGroupName = HSC_GROUP_NAME,
    hscGroupUrl = HSC_GROUP_URL,
    supportNote = SUPPORT_NOTE,
    helpContacts = listOf(
      HelpContact(
        label = "Joker Official Helpline 1",
        phoneNumber = "+923143581887",
        allowCall = true,
        allowWhatsApp = true,
      ),
      HelpContact(
        label = "Joker Official Helpline 2",
        phoneNumber = "+923112694355",
        allowCall = true,
        allowWhatsApp = true,
      ),
    ),
  )
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val preferredClass: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

object PaperSerialization {
  fun sectionsToJson(sections: List<PaperSection>): String {
    val arr = JSONArray()
    sections.forEach { section ->
      val sObj = JSONObject().apply {
        put("heading", section.heading)
        put("marksLabel", section.marksLabel)
        val qArr = JSONArray()
        section.questions.forEach { q ->
          val qObj = JSONObject().apply {
            put("number", q.number)
            put("text", q.text)
            put("marks", q.marks)
            put("answer", q.answer)
            val optArr = JSONArray()
            q.options.forEach { opt -> optArr.put(opt) }
            put("options", optArr)
          }
          qArr.put(qObj)
        }
        put("questions", qArr)
      }
      arr.put(sObj)
    }
    return arr.toString()
  }

  fun sectionsFromJson(json: String): List<PaperSection> {
    if (json.isBlank()) return emptyList()
    return try {
      val arr = JSONArray(json)
      List(arr.length()) { sIdx ->
        val sObj = arr.getJSONObject(sIdx)
        val qArr = sObj.optJSONArray("questions") ?: JSONArray()
        val questions = List(qArr.length()) { qIdx ->
          val qObj = qArr.getJSONObject(qIdx)
          val optArr = qObj.optJSONArray("options") ?: JSONArray()
          val options = List(optArr.length()) { oIdx -> optArr.optString(oIdx, "") }
            .filter { it.isNotBlank() }
          PaperQuestion(
            number = qObj.optLong("number", (qIdx + 1).toLong()),
            text = qObj.optString("text", ""),
            marks = qObj.optLong("marks", 0L),
            options = options,
            answer = qObj.optString("answer", ""),
          )
        }
        PaperSection(
          heading = sObj.optString("heading", ""),
          marksLabel = sObj.optString("marksLabel", ""),
          questions = questions,
        )
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun contactsToJson(contacts: List<HelpContact>): String {
    val arr = JSONArray()
    contacts.forEach { c ->
      arr.put(
        JSONObject().apply {
          put("label", c.label)
          put("phoneNumber", c.phoneNumber)
          put("allowCall", c.allowCall)
          put("allowWhatsApp", c.allowWhatsApp)
        }
      )
    }
    return arr.toString()
  }

  fun contactsFromJson(json: String): List<HelpContact> {
    if (json.isBlank()) return emptyList()
    return try {
      val arr = JSONArray(json)
      List(arr.length()) { idx ->
        val obj = arr.getJSONObject(idx)
        HelpContact(
          label = obj.optString("label", ""),
          phoneNumber = obj.optString("phoneNumber", ""),
          allowCall = obj.optBoolean("allowCall", true),
          allowWhatsApp = obj.optBoolean("allowWhatsApp", true),
        )
      }
    } catch (_: Exception) {
      emptyList()
    }
  }
}
