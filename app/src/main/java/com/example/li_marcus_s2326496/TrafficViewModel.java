package com.example.li_marcus_s2326496;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrafficViewModel extends AndroidViewModel {
    private static final DateTimeFormatter PUBLISHED_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm");

    public static class UiState {
        public final boolean loading;
        public final String status;
        public final List<TrafficEvent> events;
        public final int totalCount;

        UiState(boolean loading, String status, List<TrafficEvent> events, int totalCount) {
            this.loading = loading;
            this.status = status;
            this.events = events;
            this.totalCount = totalCount;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TrafficDataRepository repository;
    private final Map<TrafficEvent.Category, List<TrafficEvent>> loaded =
            new EnumMap<>(TrafficEvent.Category.class);
    private final Map<TrafficEvent.Category, String> sourceText =
            new EnumMap<>(TrafficEvent.Category.class);
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<TrafficDataRepository.DataSet> dataSets = null;
    private int dataSetIndex = -1;

    private TrafficEvent.Category category = TrafficEvent.Category.UNPLANNED;
    private String query = "";
    private LocalDate date = null;
    private boolean showMap = false;

    private Double originLat = null;
    private Double originLon = null;
    private boolean originIsFallback = false;
    private int radiusKm = 0;

    public boolean isShowMap() { return showMap; }
    public void setShowMap(boolean show) { showMap = show; }

    public int getRadiusKm() { return radiusKm; }
    public boolean hasOrigin() { return originLat != null; }
    public Double getOriginLat() { return originLat; }
    public Double getOriginLon() { return originLon; }
    public boolean isOriginFallback() { return originIsFallback; }

    public void setRadiusKm(int km) {
        radiusKm = km;
        publish();
    }

    public void setOrigin(double lat, double lon, boolean fallback) {
        originLat = lat;
        originLon = lon;
        originIsFallback = fallback;
        publish();
    }

    public TrafficViewModel(@NonNull Application app) {
        super(app);
        repository = new TrafficDataRepository(app);
    }

    public List<String> getDataSetLabels() {
        List<String> labels = new ArrayList<>();
        if (dataSets == null) return labels;
        for (int i = 0; i < dataSets.size(); i++) labels.add(labelFor(i));
        return labels;
    }

    public int getDataSetIndex() { return dataSetIndex; }

    public void selectDataSet(int index) {
        if (dataSets == null || index < 0 || index >= dataSets.size() || index == dataSetIndex) return;
        dataSetIndex = index;
        loaded.clear();
        sourceText.clear();
        load(category);
    }

    private String labelFor(int i) {
        TrafficDataRepository.DataSet set = dataSets.get(i);
        String when = set.published != null
                ? set.published.format(PUBLISHED_FORMAT)
                : getApplication().getString(R.string.unknown_date);
        return getApplication().getString(R.string.data_set_label, i + 1, when);
    }

    public LiveData<UiState> getState() { return state; }
    public TrafficEvent.Category getCategory() { return category; }
    public String getQuery() { return query; }
    public LocalDate getDate() { return date; }

    public void selectCategory(TrafficEvent.Category c, boolean refresh) {
        category = c;
        if (!refresh && loaded.containsKey(c)) {
            publish();
        } else {
            load(c);
        }
    }

    public void setQuery(String q) {
        query = q == null ? "" : q;
        publish();
    }

    public void setDate(LocalDate d) {
        date = d;
        publish();
    }

    private void load(TrafficEvent.Category c) {
        state.setValue(new UiState(true, getApplication().getString(R.string.loading),
                Collections.emptyList(), 0));
        executor.execute(() -> {
            if (dataSets == null) {
                List<TrafficDataRepository.DataSet> sets = repository.listDataSets();
                mainHandler.post(() -> {
                    dataSets = sets;
                    dataSetIndex = sets.size() - 1;
                });
                if (sets.isEmpty()) {
                    deliver(c, new ArrayList<>(), getApplication().getString(R.string.no_data_files));
                    return;
                }
                parseAndDeliver(c, sets.get(sets.size() - 1), sets.size() - 1);
            } else {
                parseAndDeliver(c, dataSets.get(dataSetIndex), dataSetIndex);
            }
        });
    }

    private void parseAndDeliver(TrafficEvent.Category c, TrafficDataRepository.DataSet set, int index) {
        List<TrafficEvent> events;
        String source = getApplication().getString(R.string.source_file, set.fileFor(c));
        try {
            events = TrafficEventParser.parse(repository.readXml(set, c), c);
        } catch (Exception e) {
            Log.e("Parsing", "EXCEPTION " + e);
            events = new ArrayList<>();
            source = source + "\n" + getApplication().getString(R.string.parse_error, e.getMessage());
        }
        events.sort(Comparator.comparing(TrafficEvent::getStartDateTime,
                Comparator.nullsLast(Comparator.<OffsetDateTime>reverseOrder())));
        final String header = source;
        final List<TrafficEvent> result = events;
        mainHandler.post(() -> {
            if (index != dataSetIndex) return;
            deliverOnMain(c, result, labelFor(index) + "\n" + header);
        });
    }

    private void deliver(TrafficEvent.Category c, List<TrafficEvent> events, String text) {
        mainHandler.post(() -> deliverOnMain(c, events, text));
    }

    private void deliverOnMain(TrafficEvent.Category c, List<TrafficEvent> events, String text) {
        loaded.put(c, events);
        sourceText.put(c, text);
        if (category == c) publish();
    }

    private void publish() {
        List<TrafficEvent> all = loaded.get(category);
        if (all == null) return;
        List<TrafficEvent> filtered = new ArrayList<>();
        for (TrafficEvent e : all) {
            Double km = hasOrigin() ? e.distanceKmFrom(originLat, originLon) : null;
            e.setDistanceKm(km);
            if (!e.matches(query)) continue;
            if (date != null && !e.isActiveOn(date)) continue;
            if (radiusKm > 0 && hasOrigin() && (km == null || km > radiusKm)) continue;
            filtered.add(e);
        }
        if (radiusKm > 0 && hasOrigin()) {
            filtered.sort(Comparator.comparing(TrafficEvent::getDistanceKm,
                    Comparator.nullsLast(Comparator.<Double>naturalOrder())));
        }
        String kind = getApplication().getString(category == TrafficEvent.Category.UNPLANNED
                ? R.string.kind_unplanned : R.string.kind_roadworks);
        String counts = filtered.size() == all.size()
                ? getApplication().getString(R.string.count_all, all.size(), kind)
                : getApplication().getString(R.string.count_filtered, filtered.size(), all.size(), kind);
        if (radiusKm > 0 && hasOrigin()) {
            counts += " " + getApplication().getString(originIsFallback
                    ? R.string.within_km_fallback : R.string.within_km_you, radiusKm);
        }
        state.setValue(new UiState(false, counts + "\n" + sourceText.get(category), filtered, all.size()));
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
