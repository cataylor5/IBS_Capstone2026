package io.github.cataylor5.ibs

import io.github.cataylor5.ibs.ui.theme.IbsAppearance
import androidx.core.view.WindowCompat
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.navigation.NavigationFragment
import com.google.android.libraries.navigation.StylingOptions
import io.github.cataylor5.ibs.ui.theme.IbsBrand

internal object IbsNavigationStyle {
    fun card(radius: Float = 16f) = GradientDrawable().apply {
        setColor(IbsAppearance.palette.surface)
        cornerRadius = radius
        setStroke(2, IbsAppearance.palette.border)
    }

    fun applyAppearance(activity: FragmentActivity) {
        val palette = IbsAppearance.palette
        val night = IbsAppearance.state.night == true
        activity.window.decorView.setBackgroundColor(palette.background)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        activity.findViewById<android.view.View>(R.id.navigation_root).setBackgroundColor(palette.background)
        activity.findViewById<TextView>(R.id.navigation_status).apply {
            setTextColor(palette.text)
            setTypeface(typeface, Typeface.BOLD)
        }
        for (id in listOf(R.id.navigation_final_label, R.id.navigation_stop_label,
            R.id.navigation_restroom_stop, R.id.navigation_message))
            activity.findViewById<TextView>(id).setTextColor(palette.text)
        for (id in listOf(R.id.navigation_stop, R.id.navigation_change, R.id.navigation_action, R.id.navigation_review)) {
            activity.findViewById<Button>(id).apply {
                backgroundTintList = null
                val fill = GradientDrawable().apply {
                    setColor(IbsBrand.CobaltArgb)
                    cornerRadius = 1000f * resources.displayMetrics.density
                }
                background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, null)
                setPadding((12 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (10 * resources.displayMetrics.density).toInt())
                textSize = 14f
                setTextColor(Color.WHITE)
                isAllCaps = false
                minHeight = (52 * resources.displayMetrics.density).toInt()
            }
        }
    }

    fun apply(activity: FragmentActivity) {
        applyAppearance(activity)
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.navigation_map) as NavigationFragment
        fragment.setStylingOptions(StylingOptions()
            .primaryDayModeThemeColor(IbsBrand.CobaltArgb)
            .secondaryDayModeThemeColor(IbsBrand.NavyArgb)
            .primaryNightModeThemeColor(IbsBrand.CobaltArgb)
            .secondaryNightModeThemeColor(IbsBrand.NavyArgb)
            .headerInstructionsTextColor(Color.WHITE)
            .headerNextStepTextColor(Color.WHITE)
            .headerDistanceValueTextColor(Color.WHITE)
            .headerDistanceUnitsTextColor(Color.WHITE)
            .headerLargeManeuverIconColor(Color.WHITE)
            .headerSmallManeuverIconColor(Color.WHITE))
    }
}
