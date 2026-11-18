package com.abdulaziz.netpass.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyVoucherPinToClipboard(context: Context, pin: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Voucher", pin))
}
