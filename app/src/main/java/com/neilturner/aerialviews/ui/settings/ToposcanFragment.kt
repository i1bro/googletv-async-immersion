package com.neilturner.aerialviews.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.screensaver.TestActivity
import com.neilturner.aerialviews.ui.toposcan.HdrSupport

class ToposcanFragment : MenuStateFragment() {
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onResume() {
        super.onResume()
        updateHdrStatus(GeneralPrefs.toposcanHdrOutput)
        findPreference<Preference>("toposcan_playback_diagnostics")?.summary =
            GeneralPrefs.toposcanPlaybackStatus.ifBlank { getString(R.string.toposcan_not_started) }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun updateHdrStatus(preference: String) {
        val support = HdrSupport.inspect(requireContext(), preference)
        findPreference<Preference>("toposcan_hdr_diagnostics")?.summary =
            listOf(support.reason, GeneralPrefs.toposcanHdrStatus).filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_toposcan, rootKey)
        findPreference<Preference>("toposcan_hdr_output")?.setOnPreferenceChangeListener { _, value ->
            updateHdrStatus(value.toString())
            true
        }
        findPreference<Preference>("toposcan_preview")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), TestActivity::class.java).putExtra(TestActivity.EXTRA_PREVIEW_TIMEOUT_SECONDS, 120))
            true
        }
    }
}
