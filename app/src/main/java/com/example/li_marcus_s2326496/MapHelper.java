package com.example.li_marcus_s2326496;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;

public final class MapHelper {
    public static final LatLng SCOTLAND = new LatLng(56.8, -4.2);
    public static final float SCOTLAND_ZOOM = 6f;

    private MapHelper() { }

    public static LatLng from(TrafficEvent e) {
        if (e.getFromLatitude() == null || e.getFromLongitude() == null) return null;
        return new LatLng(e.getFromLatitude(), e.getFromLongitude());
    }

    public static LatLng to(TrafficEvent e) {
        if (e.getToLatitude() == null || e.getToLongitude() == null) return null;
        return new LatLng(e.getToLatitude(), e.getToLongitude());
    }

    public static LatLng position(TrafficEvent e) {
        LatLng a = from(e);
        LatLng b = to(e);
        if (a == null) return b;
        if (b == null) return a;
        return new LatLng((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2);
    }

    public static BitmapDescriptor markerFor(TrafficEvent.Severity s) {
        switch (s) {
            case HIGH:   return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
            case MEDIUM: return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
            default:     return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        }
    }
}
