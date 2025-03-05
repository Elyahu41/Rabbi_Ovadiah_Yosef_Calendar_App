package com.ej.rovadiahyosefcalendar.activities;

import static com.ej.rovadiahyosefcalendar.activities.MainFragmentManager.SHARED_PREF;
import static com.ej.rovadiahyosefcalendar.activities.MainFragmentManager.sCurrentLocationName;
import static com.ej.rovadiahyosefcalendar.activities.MainFragmentManager.sCurrentTimeZoneID;
import static com.ej.rovadiahyosefcalendar.activities.MainFragmentManager.sLatitude;
import static com.ej.rovadiahyosefcalendar.activities.MainFragmentManager.sLongitude;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.ej.rovadiahyosefcalendar.R;
import com.ej.rovadiahyosefcalendar.classes.LocationResolver;
import com.ej.rovadiahyosefcalendar.classes.ROZmanimCalendar;
import com.ej.rovadiahyosefcalendar.classes.ZmanimNames;
import com.ej.rovadiahyosefcalendar.databinding.ActivityNetzBinding;
import com.kosherjava.zmanim.util.GeoLocation;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class NetzActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                Objects.requireNonNull(mContentView.getWindowInsetsController()).hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = this::hide;
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = (view, motionEvent) -> {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (AUTO_HIDE) {
                    delayedHide(AUTO_HIDE_DELAY_MILLIS);
                }
                break;
            case MotionEvent.ACTION_UP:
                view.performClick();
                break;
            default:
                break;
        }
        return false;
    };
    private ActivityNetzBinding binding;

    private static SharedPreferences mSharedPreferences;
    private static LocationResolver mLocationResolver;
    private static ROZmanimCalendar mROZmanimCalendar;
    private static boolean mIsZmanimInHebrew;
    private static boolean mIsZmanimEnglishTranslated;
    private Runnable mCountDownRunnable;
    private Runnable mCountDownTillSunsetRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityNetzBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        //binding.netzLayout.setBackground(getDawnBackground());

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(view -> toggle());

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        binding.quitButton.setOnTouchListener(mDelayHideTouchListener);
        binding.quitButton.setOnClickListener(l -> finish());

        binding.netzRefresh.setOnRefreshListener(() -> new Thread(() -> {
            Looper.prepare();
            startTimer();
            binding.netzRefresh.setRefreshing(false);
            Objects.requireNonNull(Looper.myLooper()).quit();
        }).start());

        mLocationResolver = new LocationResolver(this, new Activity());
        mSharedPreferences = getSharedPreferences(SHARED_PREF, MODE_PRIVATE);
        setZmanimLanguageBools();
        mROZmanimCalendar = getROZmanimCalendar();
        mROZmanimCalendar.setExternalFilesDir(getExternalFilesDir(null));

        startTimer();
    }

    private void startTimer() {
        Calendar calendar = Calendar.getInstance();
        mROZmanimCalendar.setCalendar(calendar);
        Date netz = mROZmanimCalendar.getHaNetz();
        boolean isMishor = false;

        if (netz == null) {
            netz = mROZmanimCalendar.getSeaLevelSunrise();
            isMishor = true;
        }

        if (netz.before(new Date())) {
            calendar.add(Calendar.DATE, 1);
            mROZmanimCalendar.setCalendar(calendar);

            netz = mROZmanimCalendar.getHaNetz();
            isMishor = false;

            if (netz == null) {
                netz = mROZmanimCalendar.getSeaLevelSunrise();
                isMishor = true;
            }
            calendar.add(Calendar.DATE, -1);
            mROZmanimCalendar.setCalendar(calendar);
        }

        ZmanimNames netzName = new ZmanimNames(mIsZmanimInHebrew, mIsZmanimEnglishTranslated);

        boolean finalIsMishor = isMishor;
        Date finalNetz = netz;
        mCountDownRunnable = new Runnable() {
            @Override
            public void run() {
                long millisUntilFinished = finalNetz.getTime() - new Date().getTime();

                if (millisUntilFinished > 0) {
                    long totalSeconds = millisUntilFinished / 1000;
                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;

                    String countdownText = netzName.getHaNetzString();

                    if (finalIsMishor) {
                        countdownText += " (" + netzName.getMishorString() + ")";
                    }

                    countdownText += netzName.getIsInString() + "\n\n";
                    countdownText += String.format(Locale.getDefault(), "%02dh:%02dm:%02ds", hours, minutes, seconds);
                    binding.fullscreenContent.setText(countdownText);

                    mHideHandler.postDelayed(this, 1000); // Re-run this Runnable in 1 second
                } else {
                    // Timer finished
                    binding.fullscreenContent.setText(getString(R.string.netz_message));
                    mROZmanimCalendar.setCalendar(Calendar.getInstance());
                    startCountDownTimerTillSunset(); // Start the sunset countdown
                }
            }
        };

        mHideHandler.post(mCountDownRunnable); // Start the initial countdown
    }

    // Method to start the countdown timer till sunset
    private void startCountDownTimerTillSunset() {
        final long sunsetTime = mROZmanimCalendar.getSunset().getTime(); // The end time for the sunset countdown
        long millisUntilFinished = sunsetTime - new Date().getTime();

        mCountDownTillSunsetRunnable = this::startTimer;
        mHideHandler.postDelayed(mCountDownTillSunsetRunnable, millisUntilFinished); // Re-run this Runnable in 1 second
    }

    // Method to cancel the countdown timers
    private void cancelCountDownTimers() {
        if (mHideHandler != null) {
            if (mCountDownRunnable != null) {
                mHideHandler.removeCallbacks(mCountDownRunnable);
            }
            if (mCountDownTillSunsetRunnable != null) {
                mHideHandler.removeCallbacks(mCountDownTillSunsetRunnable);
            }
        }
    }

    private static ROZmanimCalendar getROZmanimCalendar() {
                return new ROZmanimCalendar(new GeoLocation(
                        sCurrentLocationName,
                        sLatitude,
                        sLongitude,
                        0,// elevation doesn't matter here
                        TimeZone.getTimeZone(sCurrentTimeZoneID == null ? TimeZone.getDefault().getID() : sCurrentTimeZoneID)));
    }

    private static void setZmanimLanguageBools() {
        if (mSharedPreferences.getBoolean("isZmanimInHebrew", false)) {
            mIsZmanimInHebrew = true;
            mIsZmanimEnglishTranslated = false;
        } else if (mSharedPreferences.getBoolean("isZmanimEnglishTranslated", false)) {
            mIsZmanimInHebrew = false;
            mIsZmanimEnglishTranslated = true;
        } else {
            mIsZmanimInHebrew = false;
            mIsZmanimEnglishTranslated = false;
        }
    }
    private static @NonNull PaintDrawable getDawnBackground() {
        ShapeDrawable.ShaderFactory sf = new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(
                        0, 0, 0, height,  // Vertical gradient
                        new int[] {
                                0xFF0D0D1A,  // Deep dark blue (night fading)
                                0xFF0D0D1A,  // Deep dark blue (night fading)
                                0xFF0D0D33,  // Dark blue with slight fade
                                0xFF333366,  // Faint pre-dawn blue
                                0xFF664D80,  // Subtle purple transition
                                0xFF996633   // Faint warm orange glow near the bottom
                        },
                        null,
                        Shader.TileMode.CLAMP  // Ensures colors fill the gradient
                );
            }
        };

        PaintDrawable p = new PaintDrawable();
        p.setShape(new RectShape());
        p.setShaderFactory(sf);
        return p;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 30) {
            Objects.requireNonNull(mContentView.getWindowInsetsController()).show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    protected void onDestroy() {
        cancelCountDownTimers();
        super.onDestroy();
    }
}