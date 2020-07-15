//import static java.lang.String.format;
//
//import com.aftmi.ignitionutil.ModuleConstants;
//import com.aftmi.ignitionutil.concurrency.SimpleThreadPoolFactory;
//import com.aftmi.ignitionutil.records.RecordManagerSet;
//import com.aftmi.ignitionutil.status.ModuleStateManager;
//import com.aftmi.ignitionutil.status.SimpleStatusTagManager;
//import com.amazon.aftmi.enyo.bundle.BundleConstants;
//import com.amazon.aftmi.enyo.credentials.EC2CredentialProvider;
//import com.amazon.aftmi.enyo.gatewaylogs.LogAgent;
//import com.amazon.aftmi.enyo.gatewaylogs.LogExtractionService;
//import com.amazon.aftmi.enyo.gatewaylogs.LogExtractionTask;
//import com.amazon.aftmi.enyo.gatewaylogs.LogExtractor;
//import com.amazon.aftmi.enyo.gatewaylogs.LogPublisher;
//import com.amazon.aftmi.enyo.gatewaylogs.LogPublisherTask;
//import com.amazon.aftmi.enyo.gatewaylogs.SimpleLogManager;
//import com.amazon.aftmi.enyo.metrics.TagSetManager;
//import com.amazon.aftmi.enyo.records.LogRecordManager;
//import com.amazon.aftmi.enyo.records.MonitoringPublisherModuleRecord;
//import com.amazon.aftmi.enyo.records.MonitoringPublisherTagRecord;
//import com.amazon.aftmi.enyo.records.SimplePersistenceManager;
//import com.amazon.aftmi.enyo.snapshots.SnapshotsAgentManager;
//import com.amazon.aftmi.enyo.snapshots.SnapshotsRecordManager;
//import com.amazon.aftmi.enyo.snapshots.SnapshotsUtil;
//import com.amazon.aftmi.enyo.status.SimpleModuleState;
//import com.amazon.aftmi.enyo.wizard.WizardEMSManager;
//import com.amazon.aftmi.ignitionutilmetrics.MetricsManager;
//import com.amazon.aftmi.ignitionutilmetrics.MetricsTagProviderUpdater;
//import com.amazon.aftmi.ignitionutilmetrics.SimpleMetricsManager;
//import com.amazon.aftmi.ignitionutilmetrics.model.MetricName;
//import com.google.common.io.Closer;
//import com.inductiveautomation.ignition.common.BundleUtil;
//import com.inductiveautomation.ignition.common.execution.ExecutionManager;
//import com.inductiveautomation.ignition.gateway.logging.GatewayLoggingManager;
//import com.inductiveautomation.ignition.gateway.model.GatewayContext;
//import java.io.IOException;
//import java.time.Duration;
//import java.time.Instant;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.function.Function;
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//@SuppressWarnings("UnstableApiUsage")
//@Slf4j
//@RequiredArgsConstructor
//class GwManagerFactory implements Function<GatewayContext, GwManager> {
//    private static final ModuleConstants MODULE_CONSTANTS = ModuleConstants.ENYO;
//
//    @NonNull private final String moduleId;
//
//    @Override
//    public GwManager apply(@NonNull final GatewayContext gatewayContext) {
//        final Instant instant = Instant.now();
//        final AtomicBoolean isLogsEnabled = new AtomicBoolean();
//        final AtomicBoolean isMetricsEnabled = new AtomicBoolean();
//
//        final Closer moduleCleanupCloser = Closer.create();
//        boolean error = true;
//        try {
//            final ExecutionManager moduleExecutionManager =
//                    gatewayContext.createExecutionManager(
//                            /*name*/ format("aftmi-exec-%s", moduleId),
//                            /*threadCount*/ 12, // default used by Ignition
//                            /*threadFactory*/ SimpleThreadPoolFactory.builder()
//                                    .nameTemplate(format("aftmi-exec-%s", moduleId) + "-thread-%03d")
//                                    .uncaughtExceptionHandler(
//                                            (t, e) -> log.error("Logging uncaught exception! thread={}", t, e))
//                                    .build());
//            moduleCleanupCloser.register(moduleExecutionManager::shutdown);
//
//            final GatewayLoggingManager gatewayLoggingManager = gatewayContext.getLoggingManager();
//
//            final RecordManagerSet recordManagerSet = RecordManagerSet.fromGatewayContext(gatewayContext);
//
//            final SimpleModuleState moduleState =
//                    SimpleModuleState.create(
//                            instant,
//                            gatewayContext.getModuleManager().getModule(moduleId),
//                            gatewayContext.getRedundancyManager(),
//                            gatewayContext.getSystemProperties(),
//                            isLogsEnabled::get,
//                            isMetricsEnabled::get,
//                            moduleExecutionManager);
//
//            final ModuleStateManager<SimpleModuleState> tagManager =
//                    SimpleStatusTagManager.<SimpleModuleState>builder()
//                            .owner(MODULE_CONSTANTS.getSimpleName())
//                            .taskName("MODULE_TAG_PROVIDER")
//                            .moduleTagProviderName(MODULE_CONSTANTS.getTagProviderName())
//                            .gatewayContext(gatewayContext)
//                            .moduleExecutionManager(moduleExecutionManager)
//                            .moduleState(moduleState)
//                            .build();
//
//            final MetricsManager metricsManager =
//                    SimpleMetricsManager.builder()
//                            .registry(gatewayContext.getMetricRegistry())
//                            .tagSource(MODULE_CONSTANTS.getMetricRegistrySourceName())
//                            .build();
//
//            final SnapshotsAgentManager snapshotsAgentManager =
//                    SnapshotsAgentManager.builder() //
//                            .executionManager(moduleExecutionManager)
//                            .owner(MODULE_CONSTANTS.getSimpleName())
//                            .task("SNAPSHOTS_AGENT")
//                            .redundantRecordManager(recordManagerSet.getRedundant())
//                            .snapshotsUtil(
//                                    SnapshotsUtil.builder() //
//                                            .context(gatewayContext)
//                                            .build())
//                            .sessionName(EC2CredentialProvider.toSessionName(gatewayContext))
//                            .build();
//
//            final GwManager gwManager =
//                    GwManager.builder()
//                            .persistenceManager(
//                                    new SimplePersistenceManager(recordManagerSet, gatewayContext.getSchemaUpdater()))
//                            .bundleUtilHelper(BundleConstants.createHelper(BundleUtil.get()))
//                            .logManagerFactory(
//                                    new SimpleLogManager.Factory(
//                                            new LogAgent.Factory(
//                                                    new LogExtractionService.Factory(
//                                                            recordManagerSet,
//                                                            moduleExecutionManager,
//                                                            new LogExtractionTask.Factory(
//                                                                    recordManagerSet,
//                                                                    new LogExtractor.Factory(
//                                                                            gatewayLoggingManager, moduleState.getStatus())),
//                                                            new LogPublisherTask.Factory(
//                                                                    new LogPublisher.Factory(moduleState.getStatus()),
//                                                                    gatewayContext.getRedundancyManager(),
//                                                                    gatewayContext.getSystemProperties(),
//                                                                    moduleState.getStatus())),
//                                                    moduleState),
//                                            gatewayContext.getRedundancyManager(),
//                                            gatewayContext.getSystemProperties()))
//                            .statusTagManager(tagManager)
//                            .metricsRegistryTagManager(
//                                    SimpleStatusTagManager.<MetricsTagProviderUpdater>builder()
//                                            .owner(MODULE_CONSTANTS.getSimpleName())
//                                            .taskName("LOCAL_METRICS_TAG_PROVIDER")
//                                            .refreshInterval(Duration.ofSeconds(5))
//                                            .moduleTagProviderName(MODULE_CONSTANTS.getMetricRegistryTagProviderName())
//                                            .gatewayContext(gatewayContext)
//                                            .moduleExecutionManager(moduleExecutionManager)
//                                            .moduleState(
//                                                    MetricsTagProviderUpdater.builder()
//                                                            .registry(gatewayContext.getMetricRegistry())
//                                                            .metricFilter(
//                                                                    metricName ->
//                                                                            MODULE_CONSTANTS
//                                                                                    .getMetricRegistrySourceName()
//                                                                                    .equals(metricName.getSource()))
//                                                            .metricToTagPath(MetricName::getMetric)
//                                                            .build())
//                                            .build())
//                            .metricsManager(metricsManager)
//                            .isLogsEnabled(isLogsEnabled)
//                            .isMetricsEnabled(isMetricsEnabled)
//                            .closer(moduleCleanupCloser)
//                            .tagSetManager(
//                                    new TagSetManager(
//                                            moduleExecutionManager,
//                                            gatewayContext.getMetricRegistry(),
//                                            recordManagerSet.getRedundant(),
//                                            gatewayContext.getTagManager(),
//                                            gatewayContext.getRedundancyManager(),
//                                            gatewayContext.getSystemProperties(),
//                                            () ->
//                                                    recordManagerSet
//                                                            .getRedundant()
//                                                            .getOnlyRecord(MonitoringPublisherModuleRecord.RECORD_META),
//                                            () ->
//                                                    recordManagerSet
//                                                            .getRedundant()
//                                                            .getOnlyRecord(MonitoringPublisherTagRecord.RECORD_META),
//                                            moduleState.getStatus()))
//                            .logRecordManager(new LogRecordManager())
//                            .wizardEMSManager(new WizardEMSManager(recordManagerSet.getRedundant()))
//                            .snapshotsAgentManager(snapshotsAgentManager)
//                            .snapshotsRecordManager(
//                                    SnapshotsRecordManager.builder() //
//                                            .agentManager(snapshotsAgentManager)
//                                            .build())
//                            .build();
//
//            error = false;
//            return gwManager;
//        } finally {
//            if (error) {
//                try {
//                    moduleCleanupCloser.close();
//                } catch (final IOException e) {
//                    log.info("Failure closing during error.", e);
//                }
//            }
//        }
//    }
//}
//
//
//
