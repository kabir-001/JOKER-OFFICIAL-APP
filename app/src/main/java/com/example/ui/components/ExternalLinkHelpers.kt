package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ExternalLinkHelpers {

  fun openExternalUrl(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) {
      Toast.makeText(context, "Link is not configured yet.", Toast.LENGTH_SHORT).show()
      return
    }
    val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      trimmed
    } else {
      "https://$trimmed"
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
      Toast.makeText(context, "No app available to open this link.", Toast.LENGTH_SHORT).show()
    }
  }

  fun dialPhoneNumber(context: Context, phoneNumber: String) {
    val sanitized = phoneNumber.trim()
    if (sanitized.isBlank()) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
      Toast.makeText(context, "Unable to open phone dialer.", Toast.LENGTH_SHORT).show()
    }
  }

  fun openWhatsAppChat(context: Context, phoneNumber: String) {
    val digitsOnly = phoneNumber.filter { it.isDigit() }
    if (digitsOnly.isBlank()) {
      Toast.makeText(context, "Invalid WhatsApp number.", Toast.LENGTH_SHORT).show()
      return
    }
    val uri = Uri.parse("https://wa.me/$digitsOnly")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
      Toast.makeText(context, "Unable to open WhatsApp.", Toast.LENGTH_SHORT).show()
    }
  }

  fun sharePdfUri(context: Context, pdfUri: Uri, paperTitle: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/pdf"
      putExtra(Intent.EXTRA_STREAM, pdfUri)
      putExtra(Intent.EXTRA_SUBJECT, paperTitle)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Share or Save PDF").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }.onFailure {
      Toast.makeText(context, "Unable to share PDF.", Toast.LENGTH_SHORT).show()
    }
  }

  fun copyToClipboard(context: Context, label: String, text: String) {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    if (clipboard != null) {
      clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, trimmed))
      Toast.makeText(context, "Copied $trimmed", Toast.LENGTH_SHORT).show()
    }
  }
}
