package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer

class ActionExecutor(private val ctx: Context) {

    fun run(action: JSONObject?) {
        if (action == null) return
        try {
            when (action.optString("type")) {
                "open_app" -> openApp(action.optString("name"))
                "alarm" -> start(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, action.optInt("hour"))
                    putExtra(AlarmClock.EXTRA_MINUTES, action.optInt("minute"))
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.optString("label", "Jarvis"))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                })
                "timer" -> start(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, action.optInt("seconds", 60))
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                })
                "web_search" -> start(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + enc(action.optString("query")))))
                "maps" -> start(Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + enc(action.optString("query")))))
            }
        } catch (_: Exception) { }
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun norm(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun openApp(name: String) {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val target = norm(name)
        val match = pm.queryIntentActivities(main, 0).firstOrNull {
            val label = norm(it.loadLabel(pm).toString())
            label.contains(target) || target.contains(label)
        } ?: return
        pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.let { start(it) }
    }
}
