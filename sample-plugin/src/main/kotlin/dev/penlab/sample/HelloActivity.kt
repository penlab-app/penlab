package dev.penlab.sample

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.penlab.sdk.PenLabScreenContract
import dev.penlab.sdk.PluginResult
import dev.penlab.sdk.ResultItem

/**
 * A "screen" capability: PenLab starts this activity, the user does something, and the activity hands
 * back what to put on the page. Plain views here so the sample needs nothing but the SDK; a real plugin
 * can use anything.
 */
class HelloActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // An exported activity can be started by any app: only PenLab gets an answer.
    if (!PenLabScreenContract.isCalledByPenLab(this)) {
      finish()
      return
    }
    val request = PenLabScreenContract.readRequest(intent)
    val pad = (24 * resources.displayMetrics.density).toInt()
    setContentView(
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(pad, pad, pad, pad)
        addView(TextView(context).apply { text = getString(R.string.hello_page, request.pageIndex + 1); textSize = 20f; gravity = Gravity.CENTER })
        addView(
          Button(context).apply {
            setText(R.string.hello_button)
            setOnClickListener {
              // A text box 60 x 15 mm; PenLab centres it on the visible page when nothing was selected.
              val result = PluginResult.Items(listOf(ResultItem.text(getString(R.string.hello_text), 0f, 0f, 60f, 15f)))
              setResult(RESULT_OK, PenLabScreenContract.resultIntent(this@HelloActivity, result))
              finish()
            }
          }
        )
        addView(Button(context).apply { setText(R.string.hello_cancel); setOnClickListener { finish() } })
      }
    )
  }
}
