package com.marvelexplorer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Shared navigation for the main app areas. */
public final class BottomNavigation {
    private static final String[] LABELS = {"Perfil", "Home", "Jogos"};
    private static final String[] ICONS = {"◉", "⌂", "✦"};
    private static final Class<?>[] DESTINATIONS = {
            ProfileActivity.class, HomeActivity.class, GamesActivity.class
    };

    private BottomNavigation() {}

    /** Enables a shared edge-to-edge layout for screens that host the app navigation. */
    public static void prepareWindow(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    public static void attach(Activity activity, int selectedTab) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup root = (android.view.ViewGroup) ((android.view.ViewGroup) content).getChildAt(0);
        if (!(root instanceof LinearLayout)) return;
        boolean edgeToEdge = true;

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(edgeToEdge ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        bar.setBackgroundColor(0xFF091523);
        bar.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        bar.setElevation(dp(activity, 12));

        for (int i = 0; i < LABELS.length; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(activity, 4), dp(activity, 5), dp(activity, 4), dp(activity, 5));
            if (i == selectedTab) {
                GradientDrawable active = new GradientDrawable();
                active.setColor(0xFF142C40);
                active.setCornerRadius(dp(activity, 14));
                item.setBackground(active);
            }

            TextView icon = new TextView(activity);
            icon.setText(ICONS[i]);
            icon.setTextSize(19);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(i == selectedTab ? 0xFF45E6FF : 0xFF91AABD);

            TextView label = new TextView(activity);
            label.setText(LABELS[i]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setTypeface(null, i == selectedTab ? Typeface.BOLD : Typeface.NORMAL);
            label.setTextColor(i == selectedTab ? 0xFF45E6FF : 0xFF91AABD);
            item.addView(icon);
            item.addView(label);
            item.setContentDescription(LABELS[i]);
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v -> {
                if (tab == selectedTab) return;
                Intent intent = new Intent(activity, DESTINATIONS[tab]);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            });
            bar.addView(item, new LinearLayout.LayoutParams(0, dp(activity, 54), 1));
        }

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 66));
        root.addView(bar, barParams);

        if (edgeToEdge) {
            int left = root.getPaddingLeft();
            int top = root.getPaddingTop();
            int right = root.getPaddingRight();
            int bottom = root.getPaddingBottom();
            int barLeft = bar.getPaddingLeft();
            int barTop = bar.getPaddingTop();
            int barRight = bar.getPaddingRight();
            int barBottom = bar.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
                Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(left + safe.left, top + safe.top + dp(activity, 10),
                        right + safe.right, bottom);

                // Expand only the footer background into the navigation area. Its tab row
                // stays pinned to the same top edge, so the icons do not move vertically.
                int navigationInset = safe.bottom;
                bar.setPadding(barLeft, barTop, barRight, barBottom + navigationInset);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
                params.height = dp(activity, 66) + navigationInset;
                bar.setLayoutParams(params);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(root);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
