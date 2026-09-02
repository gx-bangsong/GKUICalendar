/*
 * Copyright (C) 2026 The Etar Calendar Authors
 */
package com.android.calendar.subscription.traffic.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import ws.xsoh.etar.R

/**
 * Phase 1c coming-soon panel for the traffic-restriction subscription.
 * Shows a short explanation and a disable button. No city/rule editing
 * yet — that lands with Phase 2.
 */
class TrafficSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val scroll = inflater.inflate(R.layout.fragment_subscription_coming_soon, container, false)
        val body: TextView = scroll.findViewById(R.id.body)
        body.text = getString(R.string.sub_traffic_coming_soon)
        activity?.title = getString(R.string.sub_traffic_name)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sub_traffic_name)
    }
}
