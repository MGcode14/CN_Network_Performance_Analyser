package com.networkanalyzer.client;

import com.networkanalyzer.model.MeasurementSample;

public interface TestProgressListener {
    void onProgress(String testId, int current, int total, double percent, String message);
    default void onSample(String testId, MeasurementSample sample) {}
}
