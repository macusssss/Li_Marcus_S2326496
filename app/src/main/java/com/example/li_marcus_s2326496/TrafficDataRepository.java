package com.example.li_marcus_s2326496;

import android.content.Context;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TrafficDataRepository {
    private static final String TAG = "TrafficDataRepository";
    public static final String FOLDER = "traffic";
    private static final String UNPLANNED_PREFIX = "unplanned";
    private static final String ROADWORKS_PREFIX = "roadworks";

    public static class DataSet implements Comparable<DataSet> {
        public final String key;
        public final String unplannedFile;
        public final String roadworksFile;
        public final OffsetDateTime published;

        DataSet(String key, String unplannedFile, String roadworksFile, OffsetDateTime published) {
            this.key = key;
            this.unplannedFile = unplannedFile;
            this.roadworksFile = roadworksFile;
            this.published = published;
        }

        public String fileFor(TrafficEvent.Category c) {
            return c == TrafficEvent.Category.UNPLANNED ? unplannedFile : roadworksFile;
        }

        @Override
        public int compareTo(DataSet o) {
            if (published == null || o.published == null) return key.compareTo(o.key);
            return published.compareTo(o.published);
        }
    }

    private final Context context;

    public TrafficDataRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<DataSet> listDataSets() {
        Map<String, String[]> pairs = new TreeMap<>();
        String[] names;
        try {
            names = context.getAssets().list(FOLDER);
        } catch (IOException e) {
            Log.e(TAG, "cannot list data files", e);
            names = null;
        }
        if (names == null) names = new String[0];
        for (String name : names) {
            String lower = name.toLowerCase();
            if (!lower.endsWith(".xml")) continue;
            String base = lower.substring(0, lower.length() - 4);
            if (base.startsWith(UNPLANNED_PREFIX)) {
                pairs.computeIfAbsent(base.substring(UNPLANNED_PREFIX.length()), k -> new String[2])[0] = name;
            } else if (base.startsWith(ROADWORKS_PREFIX)) {
                pairs.computeIfAbsent(base.substring(ROADWORKS_PREFIX.length()), k -> new String[2])[1] = name;
            }
        }
        List<DataSet> sets = new ArrayList<>();
        for (Map.Entry<String, String[]> p : pairs.entrySet()) {
            String[] files = p.getValue();
            String first = files[0] != null ? files[0] : files[1];
            sets.add(new DataSet(p.getKey(), files[0], files[1], readPublished(first)));
        }
        Collections.sort(sets);
        return sets;
    }

    public String readXml(DataSet set, TrafficEvent.Category category) throws IOException {
        String file = set.fileFor(category);
        if (file == null) return "";
        return readStream(context.getAssets().open(FOLDER + "/" + file));
    }

    private OffsetDateTime readPublished(String file) {
        try {
            String time = TrafficEventParser.parsePublicationTime(
                    readStream(context.getAssets().open(FOLDER + "/" + file)));
            return time == null ? null : OffsetDateTime.parse(time);
        } catch (IOException | XmlPullParserException | DateTimeParseException e) {
            Log.e(TAG, "cannot read publication time of " + file, e);
            return null;
        }
    }

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
