package com.example.canary.observability;

import com.example.canary.CanaryProperties;
import com.example.canary.store.MetricsWindowStore;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

@Component
public class MetricsPublisher {
    private static final Logger log = LoggerFactory.getLogger(MetricsPublisher.class);
    private final CloudWatchClient cloudWatch;
    private final MetricsWindowStore metricsWindow;
    private final CanaryProperties properties;

    public MetricsPublisher(CloudWatchClient cloudWatch, MetricsWindowStore metricsWindow, CanaryProperties properties) {
        this.cloudWatch = cloudWatch;
        this.metricsWindow = metricsWindow;
        this.properties = properties;
    }

    public void publish(String version, int httpStatus, long latencyMs) {
        Instant timestamp = Instant.now();
        long error = httpStatus >= 500 ? 1 : 0;
        Dimension versionDimension = Dimension.builder().name("Version").value(version).build();
        try {
            cloudWatch.putMetricData(PutMetricDataRequest.builder()
                    .namespace(properties.cloudWatchNamespace)
                    .metricData(List.of(
                            datum("RequestCount", 1, StandardUnit.COUNT, versionDimension, timestamp),
                            datum("ErrorCount", error, StandardUnit.COUNT, versionDimension, timestamp),
                            datum("LatencyMs", latencyMs, StandardUnit.MILLISECONDS, versionDimension, timestamp)))
                    .build());
        } catch (RuntimeException ex) {
            // The deterministic DDB window is retained so evaluation never silently loses evidence.
            log.warn("cloudwatch_metric_publish_failed version={} error={}", version, ex.getMessage());
        }
        metricsWindow.record(version, httpStatus, latencyMs, timestamp);
    }

    private static MetricDatum datum(String name, double value, StandardUnit unit, Dimension dimension, Instant timestamp) {
        return MetricDatum.builder().metricName(name).value(value).unit(unit).dimensions(dimension).timestamp(timestamp).build();
    }
}
