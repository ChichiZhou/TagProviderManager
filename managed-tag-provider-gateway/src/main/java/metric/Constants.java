package metric;

/** Collected constants of general utility. All members of this class are immutable. */
public final class Constants {
    private Constants() {
        throw new IllegalStateException();
    }

    /*
     * Namespace used when publishing tags from tag provider to CloudWatch
     */
    public static final String TAGS_NAMESPACE = "AFTMI/Ignition Monitoring";

    /*
     * Namespace used when publishing metrics from metrics registry to CloudWatch
     */
    public static final String METRICS_NAMESPACE = "AFTMI/Ignition Metrics";

    /*
     * Dimension key for system name dimension
     */
    public static final String SYSTEM_NAME_DIMENSION_KEY = "System Name";

    /*
     * Dimension key for node role dimension
     */
    public static final String NODE_ROLE_DIMENSION_KEY = "Node Role";

    /*
     * Dimension key for active node dimension
     */
    public static final String ACTIVE_NODE_DIMENSION_KEY = "Active Node";

    /*
     * Dimension key for source dimension
     */
    public static final String SOURCE_DIMENSION_KEY = "Source";

    /*
     * Source name for metrics from metrics registry without explicit source.
     */
    public static final String METRIC_REGISTRY_DEFAULT_SOURCE = "System";

    /*
     * Dimension key for active node dimension
     */
    public static final String NODE_DIMENSION_KEY = "Node";

    /*
     * Dimension value when the node is active
     */
    public static final String ACTIVE_NODE_DIMENSION_VALUE = "Active";

    /*
     * Dimension value when the node is passive
     */
    public static final String PASSIVE_NODE_DIMENSION_VALUE = "Passive";

    /*
     * Duration at which the metrics in metrics registry are published to cloudwatch (in secs)
     */
    public static final long PUBLISH_DURATION_SECS = 60;
}


