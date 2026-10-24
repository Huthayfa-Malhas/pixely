package app.pixely

import android.os.Debug
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.room3.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pixely.data.db.PixelyDatabase
import app.pixely.data.index.Detail
import app.pixely.data.model.Embedder
import app.pixely.data.model.ModelFiles
import app.pixely.data.search.PhotoSearch
import app.pixely.data.search.QueryEmbedder
import app.pixely.data.search.SearchQuery
import app.pixely.data.search.SqliteVecIndex
import app.pixely.data.search.dot
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The POC's search eval on the phone: the same 375 COCO photos and SugarCrepe captions the Mac scored
 * (the Mac proof of concept, not in this repo, wrote the asset and the Mac numbers). Needs the test photos indexed.
 * Results go to logcat (tag SearchEval) and files/eval/search_eval.txt.
 */
@RunWith(AndroidJUnit4::class)
class SearchEvalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val report = StringBuilder()

    private class Item(val cat: String, val file: String, val caption: String, val negative: String)

    @Test
    fun scoresMatchTheMac() = runBlocking {
        val json = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("search_eval.json").bufferedReader().readText())
        val items = json.getJSONArray("items").let { a ->
            List(a.length()) { a.getJSONObject(it).run { Item(getString("cat"), getString("filename"), getString("caption"), getString("negative_caption")) } }
        }
        val without = json.getJSONObject("without").let { o -> o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } } } }
        say("mac@140 ${json.getJSONObject("mac")}")

        val opened = PixelyDatabase.open(context)
        val index = SqliteVecIndex(opened.db)
        val fileOf = cocoFiles(opened.db)
        val files = items.map { it.file }.distinct().sorted()
        val idOf = fileOf.filter { (id, f) -> f in files && id in copyZero }.entries.associate { (id, f) -> f to id }
        check(idOf.size == files.size) { "Only ${idOf.size} of ${files.size} eval photos are on the phone" }
        val gallery = index.vectors(idOf.values).let { v -> files.map { v.getValue(idOf.getValue(it)) } }

        // CPU, as search uses it.
        val queries = QueryEmbedder(context, ModelFiles(context))
        val t0 = SystemClock.elapsedRealtime()
        queries.warm()
        say("cpu load ${SystemClock.elapsedRealtime() - t0} ms, pss ${Debug.getPss() / 1024} MB")
        val times = mutableListOf<Long>()
        val cpu = embedAll(items, without.keys) { text ->
            val t = SystemClock.elapsedRealtime(); queries.embed(text).also { times += SystemClock.elapsedRealtime() - t }
        }
        say("cpu query median ${times.sorted()[times.size / 2]} ms, p90 ${times.sorted()[times.size * 9 / 10]} ms")
        say("phone cpu ${score(items, files, gallery, cpu)}")
        say("phone cpu without ${withoutScore(items, files, gallery, cpu, without)}")

        // The real search over the whole library: right if the top photo is any copy of the caption's photo.
        val search = PhotoSearch(opened.db, index, queries)
        val first = items.groupBy { it.file }.map { it.value.first() }
        var top1 = 0; var top5 = 0
        val searchTimes = mutableListOf<Long>()
        for (it in first) {
            val t = SystemClock.elapsedRealtime()
            val hits = search.words(SearchQuery.parse(it.caption))
            searchTimes += SystemClock.elapsedRealtime() - t
            val tops = hits.map { h -> fileOf[h.photoId] }
            if (tops.firstOrNull() == it.file) top1++
            if (it.file in tops.take(5)) top5++
        }
        say("library ${fileOf.size} photos (${fileOf.values.toSet().size} distinct): R@1 ${pct(top1, first.size)} R@5 ${pct(top5, first.size)}, " +
            "search median ${searchTimes.sorted()[searchTimes.size / 2]} ms incl. embed")

        // GPU, for comparison with the vectors the photos were indexed with.
        val g0 = SystemClock.elapsedRealtime()
        Embedder.open(context, ModelFiles(context).find()!!, Detail.FINE.tokens, Delegate.GPU).use { gpu ->
            say("gpu load ${SystemClock.elapsedRealtime() - g0} ms on ${gpu.delegate}")
            val g = embedAll(items, without.keys) { gpu.query(it) }
            say("phone gpu ${score(items, files, gallery, g)}")
            val agree = cpu.keys.map { dot(cpu.getValue(it), g.getValue(it)) }
            say("cpu vs gpu query cosine mean ${"%.4f".format(agree.average())} min ${"%.4f".format(agree.min())}")
        }
        File(context.getExternalFilesDir("eval"), "search_eval.txt").writeText(report.toString())
    }

    private suspend fun embedAll(items: List<Item>, objects: Set<String>, embed: suspend (String) -> FloatArray): Map<String, FloatArray> =
        (items.flatMap { listOf(it.caption, it.negative) } + objects.map { "a photo of $it" } + "a photo").distinct().associateWith { embed(it) }

    /** As the Mac proof of concept scored it: true caption beats its hard negative; caption finds its photo among the 375. */
    private fun score(items: List<Item>, files: List<String>, gallery: List<FloatArray>, t: Map<String, FloatArray>): String {
        val at = files.withIndex().associate { it.value to it.index }
        val cats = items.groupBy { it.cat }.mapValues { (_, xs) ->
            pct(xs.count { val img = gallery[at.getValue(it.file)]; dot(img, t.getValue(it.caption)) > dot(img, t.getValue(it.negative)) }, xs.size)
        }
        val ranks = items.groupBy { it.file }.map { (f, xs) ->
            val q = t.getValue(xs.first().caption); val s = gallery.map { dot(it, q) }; s.count { it > s[at.getValue(f)] } + 1
        }
        return "$cats R@1 ${pct(ranks.count { it <= 1 }, ranks.size)} R@5 ${pct(ranks.count { it <= 5 }, ranks.size)}"
    }

    /** As the Mac tuning: "<caption>, without X" over photos with no X, at the shipped weight. */
    private fun withoutScore(items: List<Item>, files: List<String>, gallery: List<FloatArray>, t: Map<String, FloatArray>, without: Map<String, List<String>>): String {
        val caption = items.groupBy { it.file }.mapValues { it.value.first().caption }
        var n = 0; var kept = 0; var leaked = 0
        for ((o, xs) in without) {
            val x = t.getValue("a photo of $o"); val xFiles = xs.toSet()
            for ((g, f) in files.withIndex()) {
                if (f in xFiles) continue
                val q = t.getValue(caption.getValue(f))
                val order = gallery.indices.sortedByDescending { dot(gallery[it], q) - PhotoSearch.WITHOUT_WEIGHT * dot(gallery[it], x) }
                n++; if (order[0] == g) kept++; if (order.take(5).any { files[it] in xFiles }) leaked++
            }
        }
        return "right photo first ${pct(kept, n)}, X in top 5 ${pct(leaked, n)} (n=$n)"
    }

    private val copyZero = mutableSetOf<Long>()

    /** photo id -> COCO file name, from the test copies' names: pixely_NN_<coco>.jpg. */
    private suspend fun cocoFiles(db: PixelyDatabase): Map<Long, String> {
        val byMedia = mutableMapOf<Long, String>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE 'pixely_%'", null, null,
        )!!.use { c -> while (c.moveToNext()) byMedia[c.getLong(0)] = c.getString(1) }
        return db.useReaderConnection { c ->
            c.usePrepared("SELECT id, media_store_id FROM photo") { st ->
                buildMap {
                    while (st.step()) byMedia[st.getLong(1)]?.let { name ->
                        put(st.getLong(0), name.substringAfter('_').substringAfter('_'))
                        if (name.startsWith("pixely_00_")) copyZero += st.getLong(0)
                    }
                }
            }
        }
    }

    private fun pct(n: Int, of: Int) = "%.1f".format(100.0 * n / of)

    private fun say(line: String) {
        Log.i("SearchEval", line); report.appendLine(line)
    }
}
