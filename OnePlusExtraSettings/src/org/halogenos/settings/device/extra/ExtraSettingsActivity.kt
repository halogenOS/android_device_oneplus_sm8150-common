/*
 * Copyright (C) 2021 The LineageOS Project
 * Copyright (C) 2022 The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.halogenos.settings.device.extra

import android.R
import android.os.Bundle
import android.preference.PreferenceActivity

class ExtraSettingsActivity : PreferenceActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(
            R.id.content,
            ExtraSettingsFragment()
        ).commit()
    }
}
