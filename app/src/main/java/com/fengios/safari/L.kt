package com.fengios.safari

import java.util.Locale

/**
 * Bahasa aplikasi. Kunci = teks Inggris (bahasa default), nilai = terjemahan Indonesia.
 * Teks yang tidak ada di kamus tampil apa adanya (Inggris).
 */
object L {
    var lang = "en"

    private val id = mapOf(
        "100% = smoothest (default). Lowering it dampens touch, but scrolling may feel less smooth." to "100% = paling halus (bawaan). Menurunkan nilai meredam sentuhan, tapi scroll bisa terasa kurang mulus.",
        "Add pages from the ⋯ menu → Add to… → Reading List." to "Tambahkan halaman lewat menu ⋯ → Tambah ke… → Daftar Bacaan.",
        "Add to…" to "Tambah ke…",
        "Added to Favorites" to "Ditambahkan ke Favorit",
        "Added to Reading List" to "Ditambahkan ke Daftar Bacaan",
        "All History" to "Semua Riwayat",
        "App has been modified" to "Aplikasi telah dimodifikasi",
        "Appearance" to "Tampilan",
        "Automatically Create Topics" to "Buat Topik Otomatis",
        "Back" to "Kembali",
        "Background Blur (heavy)" to "Blur Latar (berat)",
        "Background updated" to "Latar belakang diperbarui",
        "Bar Height" to "Tinggi Bar",
        "Bookmark" to "Bookmark",
        "Bookmark added" to "Bookmark ditambahkan",
        "Bookmarks" to "Bookmark",
        "Bookmarks & History" to "Bookmark & Riwayat",
        "Cancel" to "Batal",
        "Change Background" to "Ubah Latar Belakang",
        "Change Start Page Background" to "Ubah Latar Halaman Awal",
        "Choose a background photo" to "Pilih foto latar belakang",
        "Clear" to "Hapus",
        "Clear History" to "Hapus Riwayat",
        "Clear History & Data" to "Hapus Riwayat & Data",
        "Clear History…" to "Hapus Riwayat…",
        "Clear browsing history from:" to "Hapus riwayat penelusuran dari:",
        "Close" to "Tutup",
        "Close All Tabs" to "Tutup Semua Tab",
        "Close This Tab" to "Tutup Tab Ini",
        "Couldn't load photo" to "Gagal memuat foto",
        "Delete" to "Hapus",
        "Delete (%d)" to "Hapus (%d)",
        "Delete this item?" to "Hapus item ini?",
        "Desktop Site" to "Situs Desktop",
        "Distance from Bottom" to "Jarak dari Bawah",
        "Distance from Bottom (iOS 27)" to "Jarak dari Bawah (iOS 27)",
        "Done" to "Selesai",
        "Download failed" to "Unduhan gagal",
        "Download finished • saved in the Downloads folder" to "Unduhan selesai • tersimpan di folder Unduhan",
        "Downloading “%s”" to "Mengunduh “%s”",
        "Downloading… " to "Mengunduh… ",
        "Downloads" to "Unduhan",
        "Failed" to "Gagal",
        "Favorites" to "Favorit",
        "Forward" to "Maju",
        "Google Suggestions" to "Saran Google",
        "History" to "Riwayat",
        "History and site data cleared" to "Riwayat dan data situs dihapus",
        "Language" to "Bahasa",
        "Never" to "Jangan Pernah",
        "New Tab" to "Tab Baru",
        "No app can open this file" to "Tidak ada aplikasi untuk membuka file ini",
        "No downloads" to "Tidak ada unduhan",
        "No gallery app found" to "Tidak ada aplikasi galeri",
        "No history yet." to "Belum ada riwayat.",
        "No items yet. Add them from the ⋯ menu." to "Belum ada item. Tambahkan lewat menu ⋯ di toolbar.",
        "No results." to "Tidak ada hasil.",
        "No tabs" to "Tidak ada tab",
        "Open a page first" to "Buka sebuah halaman dulu",
        "Options" to "Opsi",
        "Organize Tabs" to "Atur Tab",
        "Other Tabs" to "Tab Lainnya",
        "Page Menu" to "Menu Halaman",
        "Reading List" to "Daftar Bacaan",
        "Recent History" to "Riwayat Terbaru",
        "Reload" to "Muat Ulang",
        "Remove from Favorites?" to "Hapus dari Favorit?",
        "Remove from Reading List?" to "Hapus dari Daftar Bacaan?",
        "Request Desktop Site" to "Minta Situs Desktop",
        "Reset to Default" to "Reset ke Bawaan",
        "Search History" to "Cari Riwayat",
        "Search Tabs" to "Cari Tab",
        "Search or enter website name" to "Telusuri atau masukkan nama situs web",
        "Select Websites" to "Pilih Situs Web",
        "Settings" to "Pengaturan",
        "Share Page" to "Bagikan Halaman",
        "Show All Tabs" to "Lihat Semua Tab",
        "Show All ›" to "Tampilkan Semua ›",
        "Show Less" to "Sembunyikan",
        "Sites you visit often will appear here." to "Situs yang sering Anda buka akan muncul di sini.",
        "Tab" to "Tab",
        "Tabs from the same site are grouped into one topic automatically." to "Tab dari situs yang sama dikelompokkan otomatis menjadi satu topik.",
        "The Last Hour" to "Satu Jam Terakhir",
        "This app has been altered and cannot run. Please use the original version from its developer." to "Aplikasi ini telah diubah dan tidak dapat dijalankan. Gunakan versi asli dari pengembangnya.",
        "Today" to "Hari Ini",
        "Today and Yesterday" to "Hari Ini dan Kemarin",
        "Touch Sensitivity  %d%%" to "Sensitivitas Sentuhan  %d%%",
        "Yesterday" to "Kemarin"
    )

    fun t(en: String): String = if (lang == "id") (id[en] ?: en) else en

    fun f(en: String, vararg a: Any): String = String.format(t(en), *a)

    fun tabs(n: Int): String = if (lang == "id") "$n Tab" else if (n == 1) "1 Tab" else "$n Tabs"

    fun tabsLower(n: Int): String = if (lang == "id") "$n tab" else if (n == 1) "1 tab" else "$n tabs"

    fun locale(): Locale = if (lang == "id") Locale("id", "ID") else Locale.ENGLISH
}
