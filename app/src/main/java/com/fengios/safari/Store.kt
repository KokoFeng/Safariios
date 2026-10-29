package com.fengios.safari

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Entry(val u: String, val t: String, val ts: Long = 0L)

/** Penyimpanan lokal: riwayat, favorit, bookmark, daftar bacaan, dan ikon situs. */
class Store(private val ctx: Context) {
    private val p = ctx.getSharedPreferences("safari_data", Context.MODE_PRIVATE)
    private val cache = HashMap<String, Bitmap?>()

    fun host(u: String): String = (Uri.parse(u).host ?: u).removePrefix("www.")
    private fun root(u: String): String {
        val x = Uri.parse(u)
        return (x.scheme ?: "https") + "://" + (x.authority ?: u)
    }

    fun list(key: String): MutableList<Entry> {
        val out = mutableListOf<Entry>()
        try {
            val a = JSONArray(p.getString(key, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Entry(o.getString("u"), o.optString("t"), o.optLong("ts")))
            }
        } catch (e: Exception) { }
        return out
    }

    private fun save(key: String, l: List<Entry>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("u", it.u).put("t", it.t).put("ts", it.ts)) }
        p.edit().putString(key, a.toString()).apply()
    }

    fun add(key: String, u: String, t: String) {
        if (!u.startsWith("http")) return
        val l = list(key).filter { it.u != u }.toMutableList()
        l.add(0, Entry(u, t, System.currentTimeMillis()))
        save(key, l.take(200))
    }

    fun remove(key: String, u: String) { save(key, list(key).filter { it.u != u }) }

    // ---- riwayat
    fun history(): List<Entry> = list("hist")
    fun clearHistory() { p.edit().remove("hist").apply() }
    fun clearHistorySince(ts: Long) { save("hist", list("hist").filter { it.ts < ts }) }
    fun removeHistory(ts: Long) { save("hist", list("hist").filter { it.ts != ts }) }

    private fun visits(): JSONObject =
        try { JSONObject(p.getString("visits", "{}")!!) } catch (e: Exception) { JSONObject() }

    fun addHistory(u: String, t: String) {
        if (!u.startsWith("http")) return
        val l = list("hist")
        val now = System.currentTimeMillis()
        if (l.isNotEmpty() && l[0].u == u && now - l[0].ts < 8000) {
            if (t.isNotBlank()) l[0] = Entry(u, t, l[0].ts)
            save("hist", l)
            return
        }
        l.add(0, Entry(u, t, now))
        save("hist", l.take(500))
        val v = visits()
        val h = host(u)
        val o = v.optJSONObject(h) ?: JSONObject()
        val path = Uri.parse(u).path ?: ""
        o.put("c", o.optInt("c") + 1)
        o.put("u", root(u))
        if (path.isEmpty() || path == "/" || !o.has("t")) o.put("t", if (t.isBlank()) h else t)
        v.put(h, o)
        p.edit().putString("visits", v.toString()).apply()
    }

    // ---- favorit = yang disematkan + situs yang paling sering dikunjungi
    fun favorites(): List<Entry> {
        val out = list("favs").toMutableList()
        val seen = out.map { host(it.u) }.toMutableSet()
        val v = visits()
        val keys = mutableListOf<String>()
        val ki = v.keys()
        while (ki.hasNext()) keys.add(ki.next())
        keys.sortedByDescending { v.getJSONObject(it).optInt("c") }.forEach { k ->
            if (k !in seen) {
                val o = v.getJSONObject(k)
                out.add(Entry(o.optString("u", "https://$k"), o.optString("t", k)))
                seen.add(k)
            }
        }
        return out.take(40)
    }

    fun pin(u: String, t: String) {
        if (!u.startsWith("http")) return
        val l = list("favs").filter { host(it.u) != host(u) }.toMutableList()
        l.add(Entry(u, t, System.currentTimeMillis()))
        save("favs", l)
    }

    fun removeFavorite(u: String) {
        val h = host(u)
        save("favs", list("favs").filter { host(it.u) != h })
        val v = visits()
        v.remove(h)
        p.edit().putString("visits", v.toString()).apply()
    }

    // ---- foto latar halaman awal
    private fun bgFile() = File(ctx.filesDir, "start_bg.jpg")
    fun hasBg() = bgFile().exists()
    fun clearBg() { bgFile().delete() }
    fun loadBg(): Bitmap? =
        if (hasBg()) try { BitmapFactory.decodeFile(bgFile().path) } catch (e: Exception) { null } else null

    fun saveBg(uri: Uri): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 2000 || opts.outHeight / sample > 2000) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return false
            val rot = ctx.contentResolver.openInputStream(uri)?.use {
                try {
                    when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                        6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                    }
                } catch (e: Exception) { 0f }
            } ?: 0f
            val out = if (rot != 0f)
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            else bmp
            bgFile().outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        } catch (e: Exception) { false }
    }

    // ---- wallpaper: foto pilihan pengguna, atau gambar bawaan aplikasi
    private var defBg: Bitmap? = null

    fun wallpaper(): Bitmap {
        loadBg()?.let { return it }
        defBg?.let { return it }
        val b = BitmapFactory.decodeResource(ctx.resources, R.drawable.default_wallpaper)
        defBg = b
        return b
    }

    // ---- ikon situs
    private fun iconFile(h: String) = File(ctx.filesDir, "ic_" + h.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    fun saveIcon(h: String, b: Bitmap) {
        try {
            val s = Bitmap.createScaledBitmap(b, 64, 64, true)
            iconFile(h).outputStream().use { s.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cache[h] = s
        } catch (e: Exception) { }
    }

    fun icon(h: String): Bitmap? {
        cache[h]?.let { return it }
        val f = iconFile(h)
        if (!f.exists()) return null
        val b = try { BitmapFactory.decodeFile(f.path) } catch (e: Exception) { null }
        if (b != null) cache[h] = b
        return b
    }
}
