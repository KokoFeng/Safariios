package com.fengios.safari

import java.security.MessageDigest

/**
 * Teks pengembang tidak disimpan sebagai string/resource biasa, tetapi sebagai byte yang diacak,
 * lalu diverifikasi dengan hash SHA-256. Kalau ada yang mengubahnya, aplikasi menolak berjalan.
 */
object DevInfo {
    private val k = intArrayOf(130, 61, 227, 139, 123, 187, 74, 181, 66, 207, 191, 120, 158, 159, 84, 163, 237, 209, 135, 157, 9, 112, 118, 88, 213, 220)
    private val d = intArrayOf(198, 95, 163, 3, 51, 247, 100, 1, 104, 46, 203, 165, 44, 85, 156, 45, 244, 21, 82, 119, 243, 199, 158, 216, 100, 103)
    private const val H = "48241488fa17296307b1fe2feef84fe6cedb0ee5517efa8b0403a694d8740476"

    private fun raw(): String {
        val sb = StringBuilder()
        for (i in d.indices) sb.append((((d[i] - i * 7) and 0xFF) xor k[i]).toChar())
        return sb.toString()
    }

    fun label(): String = raw()

    fun valid(): Boolean = try {
        val md = MessageDigest.getInstance("SHA-256").digest(raw().toByteArray(Charsets.UTF_8))
        md.joinToString("") { "%02x".format(it) } == H
    } catch (e: Exception) { false }
}
