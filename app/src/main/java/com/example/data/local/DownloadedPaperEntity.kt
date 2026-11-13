package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.PaperSerialization
import com.example.data.model.TargetPaper

@Entity(tableName = "downloaded_papers")
data class DownloadedPaperEntity(
  @PrimaryKey val paperId: String,
  val title: String,
  val subject: String,
  val classLevel: String,
  val examTerm: String,
  val totalMarks: Long,
  val durationText: String,
  val instructions: String,
  val externalPdfUrl: String,
  val sectionsJson: String,
  val pdfFilePath: String,
  val downloadedAtMillis: Long,
  val fileSizeBytes: Long,
) {
  fun toTargetPaper(): TargetPaper =
    TargetPaper(
      id = paperId,
      title = title,
      subject = subject,
      classLevel = classLevel,
      examTerm = examTerm,
      totalMarks = totalMarks,
      durationText = durationText,
      instructions = instructions,
      externalPdfUrl = externalPdfUrl,
      sections = PaperSerialization.sectionsFromJson(sectionsJson),
      status = TargetPaper.STATUS_PUBLISHED,
    )
}
