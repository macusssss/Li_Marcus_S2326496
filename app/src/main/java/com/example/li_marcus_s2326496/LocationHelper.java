package com.example.li_marcus_s2326496;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

public final class LocationHelper {
    public static final double FALLBACK_LAT = 55.8611;
    public static final double FALLBACK_LON = -4.2502;

    public interface Callback {
        void onLocation(double lat, double lon, boolean isFallback);
    }

    private LocationHelper() { }

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static final double SCOTLAND_LAT = 57.0;
    private static final double SCOTLAND_LON = -4.5;
    private static final double SCOTLAND_RADIUS_KM = 450;

    public static void getLocation(Context context, Callback callback) {
        getRawLocation(context, (lat, lon, fallback) -> {
            if (!fallback && distanceKm(lat, lon, SCOTLAND_LAT, SCOTLAND_LON) > SCOTLAND_RADIUS_KM) {
                callback.onLocation(FALLBACK_LAT, FALLBACK_LON, true);
            } else {
                callback.onLocation(lat, lon, fallback);
            }
        });
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        float[] out = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, out);
        return out[0] / 1000.0;
    }

    @SuppressWarnings("MissingPermission")
    private static void getRawLocation(Context context, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        if (!hasPermission(context)) {
            main.post(() -> callback.onLocation(FALLBACK_LAT, FALLBACK_LON, true));
            return;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            main.post(() -> callback.onLocation(FALLBACK_LAT, FALLBACK_LON, true));
            return;
        }

        Location lastKnown = null;
        for (String provider : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(provider);
            if (l != null && (lastKnown == null || l.getTime() > lastKnown.getTime())) lastKnown = l;
        }
        final Location stale = lastKnown;
        final Callback giveUp = (a, b, c) -> {
            if (stale != null) callback.onLocation(stale.getLatitude(), stale.getLongitude(), false);
            else callback.onLocation(FALLBACK_LAT, FALLBACK_LON, true);
        };

        String provider = freshProvider(lm);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && provider != null) {
            final boolean[] done = { false };
            CancellationSignal cancel = new CancellationSignal();
            lm.getCurrentLocation(provider, cancel, context.getMainExecutor(), l -> {
                if (done[0]) return;
                done[0] = true;
                if (l != null) callback.onLocation(l.getLatitude(), l.getLongitude(), false);
                else giveUp.onLocation(0, 0, true);
            });
            main.postDelayed(() -> {
                if (done[0]) return;
                done[0] = true;
                cancel.cancel();
                giveUp.onLocation(0, 0, true);
            }, 8000);
            return;
        }
        main.post(() -> giveUp.onLocation(0, 0, true));
    }

    private static String freshProvider(LocationManager lm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        return null;
    }
}
