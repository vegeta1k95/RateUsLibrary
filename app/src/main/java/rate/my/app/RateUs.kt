package rate.my.app

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import per.wsj.library.AndRatingBar
import kotlin.math.roundToInt

@Keep
object RateMyApp {

    private const val PREFERENCES = "rateus"
    private const val KEY_RATING = "rating"
    private const val KEY_LAST = "last"

    private const val MARKET_APP_URL = "market://details?id=%s"
    private const val MARKET_WEB_URL = "https://play.google.com/store/apps/details?id=%s"

    private fun isTimeToRequest(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST, 0)
        return if (last == 0L) {
            true
        } else ((now - last) / (1000 * 60 * 60)).toInt() >= 1 // ONE HOUR
    }

    private fun setLastRequested(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST, timestamp).apply()
    }

    private fun setRating(context: Context, rating: Float) {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_RATING, rating).apply()
    }

    private fun isRated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return prefs.contains(KEY_RATING)
    }

    @Keep
    @JvmStatic
    fun showDialog(
        activity: Activity?,
        doChecks: Boolean = false,
        onFinished: Runnable? = null
    ) {
        if (activity == null) return
        if (doChecks) {
            if (isRated(activity) || !isTimeToRequest(activity)) {
                if (onFinished != null && !activity.isDestroyed && !activity.isFinishing) onFinished.run()
                return
            }
            setLastRequested(activity, System.currentTimeMillis())
        }

        val dialog = Dialog(activity, R.style.CustomDialog)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window!!.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_rate_us)
        dialog.window!!.setGravity(Gravity.CENTER)
        dialog.show()

        val rate = dialog.findViewById<Button>(R.id.btn_rate)
        val emoji = dialog.findViewById<ImageView>(R.id.img_emoji)
        val title = dialog.findViewById<TextView>(R.id.txt_rateus_title)
        val descr = dialog.findViewById<TextView>(R.id.txt_rateus_description)
        val bar = dialog.findViewById<AndRatingBar>(R.id.rate_bar)

        val marketApp = String.format(MARKET_APP_URL, activity.packageName)
        val marketWeb = String.format(MARKET_WEB_URL, activity.packageName)

        bar.setOnRatingChangeListener { _: AndRatingBar?, stars: Float, _: Boolean ->

            val rating = stars.roundToInt()

            val icon = when (rating) {
                5 -> R.drawable.rate_five
                4 -> R.drawable.rate_four
                3 -> R.drawable.rate_three
                2 -> R.drawable.rate_two
                else -> R.drawable.rate_one
            }

            val text = when  {
                rating >= 4 -> R.string.rate_us_rating_good
                else -> R.string.rate_us_rating_bad
            }

            val msg = when  {
                rating >= 4 -> R.string.rate_us_rating_good_descr
                else -> R.string.rate_us_rating_bad_descr
            }

            emoji.setImageDrawable(AppCompatResources.getDrawable(activity, icon))
            title.setText(text)
            descr.setText(msg)
        }

        rate.setOnClickListener {
            if (bar.rating > 3f) {
                setRating(activity, bar.rating)
                val manager = ReviewManagerFactory.create(activity)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task: Task<ReviewInfo?> ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        val flow = manager.launchReviewFlow(activity, reviewInfo)
                        flow.addOnCompleteListener {
                            Toast.makeText(activity, R.string.rate_us_thanks, Toast.LENGTH_SHORT)
                                .show()
                            dialog.dismiss()
                            if (onFinished != null && !activity.isDestroyed && !activity.isFinishing)
                                onFinished.run()
                        }
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(marketApp))
                            activity.startActivity(intent)
                        } catch (exc: ActivityNotFoundException) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(marketWeb))
                            activity.startActivity(intent)
                        }
                        dialog.dismiss()
                        if (onFinished != null && !activity.isDestroyed && !activity.isFinishing)
                            onFinished.run()
                    }
                }
            } else {
                Toast.makeText(activity, R.string.rate_us_thanks, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                if (onFinished != null && !activity.isDestroyed && !activity.isFinishing)
                    onFinished.run()
            }
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            if (onFinished != null && !activity.isDestroyed && !activity.isFinishing)
                onFinished.run()
        }
        dialog.show()
    }
}