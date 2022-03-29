package com.sdk.rateuslibrary;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.tasks.Task;

import per.wsj.library.AndRatingBar;

public class RateUs {

    private static final String PREFERENCES = "rate_us";
    private static final String RATE_US = "RATE_US";
    private static final String LAST_REQUESTED = "RATE_US_LAST";

    private static final String MARKET_APP_URL = "market://details?id=%s";
    private static final String MARKET_WEB_URL = "https://play.google.com/store/apps/details?id=%s";

    private static boolean isTimeToRequest(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);

        long now = System.currentTimeMillis();
        long last = prefs.getLong(LAST_REQUESTED, 0);

        if (last == 0) {
            return true;
            /*
            try {
                last = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime;
            } catch (PackageManager.NameNotFoundException e) {
                last = now;
            }
            */
        }

        return (int) ((now - last) / (1000 * 60 * 60 * 24)) >= 2;
    }

    private static void setLastRequested(Context context, long timestamp) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(LAST_REQUESTED, timestamp);
        editor.apply();
    }

    private static void setRating(Context context, float rating) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(RATE_US, rating);
        editor.apply();
    }

    private static boolean isRated(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return prefs.contains(RATE_US);
    }

    public static void showDialog(final Activity activity, boolean finish, boolean ignoreCheck) {

        if (!ignoreCheck && (isRated(activity) || !isTimeToRequest(activity))) {
            if (finish)
                activity.finish();
            return;
        }

        setLastRequested(activity, System.currentTimeMillis());

        Dialog dialog = new Dialog(activity, R.style.CustomDialog);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.rate_us);
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.show();

        Button rate = dialog.findViewById(R.id.btn_rate);
        ImageView emoji = dialog.findViewById(R.id.img_emoji);
        TextView title = dialog.findViewById(R.id.txt_rateus_title);
        TextView descr = dialog.findViewById(R.id.txt_rateus_description);
        TextView text = dialog.findViewById(R.id.txt_rateus_text);
        View container = dialog.findViewById(R.id.container_text);
        AndRatingBar bar = dialog.findViewById(R.id.rate_bar);

        final String market_app = String.format(MARKET_APP_URL, activity.getPackageName());
        final String market_web = String.format(MARKET_WEB_URL, activity.getPackageName());

        bar.setOnRatingChangeListener((ratingBar, rating, fromUser) -> {

            text.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);

            switch (Math.round(rating)) {
                case 5:
                    emoji.setImageDrawable(activity.getDrawable(R.drawable.emoji_love));
                    title.setText(R.string.rate_us_rating_good);
                    descr.setText(R.string.rate_us_rating_good_descr);
                    break;
                case 4:
                    emoji.setImageDrawable(activity.getDrawable(R.drawable.emoji_ok));
                    title.setText(R.string.rate_us_rating_good);
                    descr.setText(R.string.rate_us_rating_good_descr);
                    break;
                case 3:
                    emoji.setImageDrawable(activity.getDrawable(R.drawable.emoji_sad));
                    title.setText(R.string.rate_us_rating_bad);
                    descr.setText(R.string.rate_us_rating_bad_descr);
                    break;
                case 2:
                case 1:
                    emoji.setImageDrawable(activity.getDrawable(R.drawable.emoji_cry));
                    title.setText(R.string.rate_us_rating_bad);
                    descr.setText(R.string.rate_us_rating_bad_descr);
                    break;
            }

        });

        rate.setOnClickListener(v -> {
            if (bar.getRating() > 3F) {
                setRating(activity, bar.getRating());

                ReviewManager manager = ReviewManagerFactory.create(activity);
                Task<ReviewInfo> request = manager.requestReviewFlow();
                request.addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        ReviewInfo reviewInfo = task.getResult();
                        Task<Void> flow = manager.launchReviewFlow(activity, reviewInfo);
                        flow.addOnCompleteListener(t -> {
                            Toast.makeText(activity, R.string.rate_us_thanks, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            if (finish)
                                activity.finish();
                        });
                    } else {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(market_app));
                            activity.startActivity(intent);
                        } catch (ActivityNotFoundException exc) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(market_web));
                            activity.startActivity(intent);
                        }

                        dialog.dismiss();
                        if (finish)
                            activity.finish();
                    }
                });

            } else {
                Toast.makeText(activity, R.string.rate_us_thanks, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (finish)
                    activity.finish();
            }
        });
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(dg -> {
            if (finish)
                activity.finish();
        });
        dialog.show();

    }

}

