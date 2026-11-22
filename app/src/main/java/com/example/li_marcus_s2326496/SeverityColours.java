package com.example.li_marcus_s2326496;

import android.content.Context;

import androidx.core.content.ContextCompat;

public final class SeverityColours {
    private SeverityColours() { }

    public static int colourFor(Context c, TrafficEvent.Severity s) {
        switch (s) {
            case HIGH:   return ContextCompat.getColor(c, R.color.severity_high);
            case MEDIUM: return ContextCompat.getColor(c, R.color.severity_medium);
            default:     return ContextCompat.getColor(c, R.color.severity_low);
        }
    }

    public static int labelFor(TrafficEvent.Severity s) {
        switch (s) {
            case HIGH:   return R.string.severity_high;
            case MEDIUM: return R.string.severity_medium;
            default:     return R.string.severity_low;
        }
    }
}
