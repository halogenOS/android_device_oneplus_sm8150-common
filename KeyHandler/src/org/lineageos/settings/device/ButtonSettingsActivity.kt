/*
 * Copyright (C) 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.R
import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class ButtonSettingsActivity : CollapsingToolbarBaseActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentManager.beginTransaction().replace(
            R.id.content,
            ButtonSettingsFragment()
        ).commit()
    }
}
