package dev.penlab.sample

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.penlab.sdk.PenLabScreenContract
import dev.penlab.sdk.PluginResult
import dev.penlab.sdk.ResultItem

/**
 * A "screen" capability: PenLab starts this activity, the user does something, and the activity hands
 * back what to put on the page. Plain views here so the sample needs nothing but the SDK; a real plugin
 * can use anything. It also shows the theme hook: [PenLabScreenContract.theme] tells the screen whether
 * PenLab is dark or light and what its accent colour is, so the screen can match.
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
    val theme = PenLabScreenContract.theme(intent)
    val dp = resources.displayMetrics.density
    val pad = (24 * dp).toInt()

    // Colours derived from the theme: background, text, and a button text colour readable on the accent.
    val background = if (theme.darkUi) 0xFF121417.toInt() else 0xFFF7F8FA.toInt()
    val textColor = if (theme.darkUi) 0xFFE8EAED.toInt() else 0xFF1B1D21.toInt()
    val onAccent = if (isLight(theme.accentArgb)) Color.BLACK else Color.WHITE

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(background)
        setPadding(pad, pad, pad, pad)
        addView(TextView(context).apply { text = getString(R.string.hello_page, request.pageIndex + 1); textSize = 20f; gravity = Gravity.CENTER; setTextColor(textColor) })
        addView(
          Button(context).apply {
            setText(R.string.hello_button)
            setTextColor(onAccent)
            backgroundTintList = ColorStateList.valueOf(theme.accentArgb)
            setOnClickListener {
              // A text box 60 x 15 mm; PenLab centres it on the visible page when nothing was selected.
              val result = PluginResult.Items(listOf(ResultItem.text(getString(R.string.hello_text), 0f, 0f, 60f, 15f)))
              setResult(RESULT_OK, PenLabScreenContract.resultIntent(this@HelloActivity, result))
              finish()
            }
          }
        )
        addView(
          Button(context).apply {
            setText(R.string.hello_cancel)
            setTextColor(textColor)
            backgroundTintList = ColorStateList.valueOf(if (theme.darkUi) 0xFF2A2D33.toInt() else 0xFFE3E6EB.toInt())
            setOnClickListener { finish() }
          }
        )
      }
    setContentView(root)

    // Target SDK 36 draws edge to edge: the window has no inset of its own. Keep the content clear of the
    // system bars (the background still paints behind them, so the status bar matches the page).
    root.setOnApplyWindowInsetsListener { view, insets ->
      val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
      view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
      insets
    }
    // Status/navigation bar icons: dark icons on a light page, light icons on a dark one.
    window.insetsController?.setSystemBarsAppearance(
      if (theme.darkUi) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
      WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
    )
  }

  /** True if black text is the more readable choice on [argb] (WCAG-style relative luminance above the midpoint). */
  private fun isLight(argb: Int): Boolean {
    fun channel(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
    val luminance = 0.2126 * channel(Color.red(argb)) + 0.7152 * channel(Color.green(argb)) + 0.0722 * channel(Color.blue(argb))
    return luminance > 0.179
  }
}
