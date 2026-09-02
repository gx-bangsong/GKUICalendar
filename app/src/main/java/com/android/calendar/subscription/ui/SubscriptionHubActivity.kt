/*
 * Copyright (C) 2026 The Etar Calendar Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.android.calendar.subscription.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.calendar.Utils
import com.android.calendar.lunar.LunarMode
import com.android.calendar.settings.EXTRA_SHOW_FRAGMENT
import com.android.calendar.settings.GeneralPreferences
import com.android.calendar.settings.SettingsActivity
import com.android.calendar.subscription.SubscriptionProvider
import com.android.calendar.subscription.SubscriptionRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import ws.xsoh.etar.R

/**
 * Lists installed [SubscriptionProvider]s with enable/disable toggles.
 *
 * Interaction model (matches Settings-style list rows):
 *  * tapping the **row** opens that provider's settings immediately —
 *    enabling it first if it was off. Navigation never waits on the switch
 *    thumb animation.
 *  * tapping the **switch** only enables/disables, without navigating.
 *
 * Built with a manually populated LinearLayout (the list is short, < 10
 * rows). sw600dp layout centers the list at 560dp.
 */
class SubscriptionHubActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_hub)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        container = findViewById(R.id.list_container)
        buildList()
    }

    override fun onResume() {
        super.onResume()
        // Summaries ("3 days until next period", cycle length, ...) change in
        // the settings screens, so rebuild when coming back.
        buildList()
    }

    private fun buildList() {
        container.removeAllViews()
        addHeader(getString(R.string.sub_section_subscribed))
        for (p in SubscriptionRegistry.getAll()) {
            addProviderRow(p)
        }
    }

    private fun addHeader(label: String) {
        val tv = LayoutInflater.from(this).inflate(
            R.layout.item_subscription_section, container, false) as TextView
        tv.text = label
        container.addView(tv)
    }

    private fun addProviderRow(p: SubscriptionProvider) {
        val root = LayoutInflater.from(this).inflate(
            R.layout.item_subscription, container, false)
        val icon: ImageView = root.findViewById(R.id.icon)
        val title: TextView = root.findViewById(R.id.title)
        val subtitle: TextView = root.findViewById(R.id.subtitle)
        val toggle: MaterialSwitch = root.findViewById(R.id.toggle)

        icon.setImageResource(p.iconRes)
        title.text = getString(p.displayNameRes)
        val summary = p.getCurrentSummary(this)
        subtitle.text = summary ?: getString(p.summaryRes)

        // Bind state before attaching the listener so restoring the row's
        // state never fires a spurious enable/disable.
        toggle.isChecked = p.isEnabled(this)
        toggle.setOnCheckedChangeListener { _, isChecked -> setEnabled(p, isChecked) }

        // The switch is not independently clickable (see item_subscription.xml),
        // so give it its own tap target that toggles without navigating.
        toggle.isClickable = true
        toggle.isFocusable = true

        root.setOnClickListener {
            if (!p.isEnabled(this)) {
                // Update the model first, then reflect it on the switch. The
                // thumb animates while the next activity is already starting.
                setEnabled(p, true)
                toggle.isChecked = true
            }
            openSettings(p)
        }
        container.addView(root)
    }

    /** Applies an enable/disable transition, skipping no-op writes. */
    private fun setEnabled(p: SubscriptionProvider, enabled: Boolean) {
        val was = p.isEnabled(this)
        if (was == enabled) return
        setProviderEnabled(p.id, enabled)
        if (enabled) p.onEnabled(this) else p.onDisabled(this)
    }

    private fun openSettings(p: SubscriptionProvider) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(EXTRA_SHOW_FRAGMENT, p.getSettingsFragmentClass().name)
        startActivity(intent)
    }

    private fun setProviderEnabled(id: String, enabled: Boolean) {
        when (id) {
            "lunar" -> {
                val sp = getSharedPreferences(
                    GeneralPreferences.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                sp.edit().putString("pref_lunar_mode",
                    if (enabled) "contextual" else "off").apply()
            }
            else -> prefs.edit().putBoolean("sub_${id}_enabled", enabled).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "subscription_hub"
    }
}
