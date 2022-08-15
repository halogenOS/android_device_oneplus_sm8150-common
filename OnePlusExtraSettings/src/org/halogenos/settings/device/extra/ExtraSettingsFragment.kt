/*
 * Copyright (C) 2021 The LineageOS Project
 * Copyright (C) 2022 The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.halogenos.settings.device.extra

import android.os.Bundle
import android.view.MenuItem
import androidx.preference.PreferenceFragment

class ExtraSettingsFragment : PreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.extra_settings)
        activity.actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun addPreferencesFromResource(preferencesResId: Int) {
        super.addPreferencesFromResource(preferencesResId)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                activity.finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
