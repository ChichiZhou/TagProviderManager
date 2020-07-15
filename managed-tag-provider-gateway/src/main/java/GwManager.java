//import static java.lang.String.format;
//
//import com.aftmi.ignitionutil.bundle.BundleUtilHelper;
//import com.aftmi.ignitionutil.gateway.GatewayModuleManager;
//import com.aftmi.ignitionutil.records.RecordManager;
//import com.aftmi.ignitionutil.records.RecordManagerSet;
//import com.aftmi.ignitionutil.records.RecordUtil;
//import com.aftmi.ignitionutil.status.ModuleStateManager;
//import com.amazon.aftmi.enyo.gatewaylogs.LogManagerCreationException;
//import com.amazon.aftmi.enyo.gatewaylogs.SimpleLogManager;
//import com.amazon.aftmi.enyo.metrics.TagManagerCreationException;
//import com.amazon.aftmi.enyo.metrics.TagSetManager;
//import com.amazon.aftmi.enyo.records.LogRecordManager;
//import com.amazon.aftmi.enyo.records.MonitoringPublisherModuleRecord;
//import com.amazon.aftmi.enyo.records.PersistenceManager;
//import com.amazon.aftmi.enyo.snapshots.SnapshotsAgentManager;
//import com.amazon.aftmi.enyo.snapshots.SnapshotsRecordManager;
//import com.amazon.aftmi.enyo.status.SimpleModuleState;
//import com.amazon.aftmi.enyo.wizard.WizardEMSManager;
//import com.amazon.aftmi.ignitionutilmetrics.MetricsManager;
//import com.amazon.aftmi.ignitionutilmetrics.MetricsTagProviderUpdater;
//import com.amazon.aftmi.ignitionutilmetrics.model.GaugeMetricSpec;
//import com.google.common.collect.ImmutableList;
//import com.google.common.io.Closer;
//import com.inductiveautomation.ignition.gateway.localdb.SchemaUpdater;
//import com.inductiveautomation.ignition.gateway.localdb.persistence.IRecordListener;
//import com.inductiveautomation.ignition.gateway.localdb.persistence.RecordMeta;
//import com.inductiveautomation.ignition.gateway.web.models.KeyValue;
//import java.io.IOException;
//import java.sql.SQLException;
//import java.time.Duration;
//import java.util.concurrent.atomic.AtomicBoolean;
//import javax.annotation.concurrent.GuardedBy;
//import lombok.AccessLevel;
//import lombok.Getter;
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//@SuppressWarnings("UnstableApiUsage")
//@Slf4j
//@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
//public class GwManager
//        implements GatewayModuleManager,
//        IRecordListener<MonitoringPublisherModuleRecord>,
//        PersistenceManager.RecordProvisioner<MonitoringPublisherModuleRecord> {
//    @NonNull private final PersistenceManager persistenceManager;
//    @NonNull private final BundleUtilHelper bundleUtilHelper;
//    @NonNull private final SimpleLogManager.Factory logManagerFactory;
//    @Getter @NonNull private final ModuleStateManager<SimpleModuleState> statusTagManager;
//
//    @Getter(AccessLevel.PACKAGE)
//    @NonNull
//    private final AtomicBoolean isLogsEnabled;
//
//    @NonNull private final AtomicBoolean isMetricsEnabled;
//
//    @NonNull private final Closer closer;
//    @NonNull private final TagSetManager tagSetManager;
//    @NonNull private final LogRecordManager logRecordManager;
//    @NonNull private final WizardEMSManager wizardEMSManager;
//    @NonNull private final ModuleStateManager<MetricsTagProviderUpdater> metricsRegistryTagManager;
//    @NonNull private final MetricsManager metricsManager;
//    @NonNull private final SnapshotsAgentManager snapshotsAgentManager;
//    @NonNull private final SnapshotsRecordManager snapshotsRecordManager;
//
//    private final long startedEpochMillis = System.currentTimeMillis();
//
//    private final Object localStateLock = new Object();
//
//    @GuardedBy("localStateLock")
//    private SimpleLogManager simpleLogManager = null;
//
//    @Override
//    public void setup() {
//        log.info("setup()");
//
//        closer.register(metricsManager::removeAllManaged);
//
//        metricsRegistryTagManager.startup();
//        closer.register(metricsRegistryTagManager::shutdown);
//
//        metricsManager.registerGauge(
//                GaugeMetricSpec.<Duration>builder()
//                        .name("Module/InstalledMillis")
//                        .valueSupplier(() -> Duration.ofMillis(System.currentTimeMillis() - startedEpochMillis))
//                        .build());
//
//        bundleUtilHelper.startup();
//        closer.register(bundleUtilHelper::shutdown);
//
//        persistenceManager.setup(
//                ImmutableList.<PersistenceManager.RecordProvisioner<?>>builder()
//                        .add(this)
//                        .add(logRecordManager)
//                        .add(tagSetManager)
//                        .add(wizardEMSManager)
//                        .add(snapshotsRecordManager)
//                        .build());
//        closer.register(persistenceManager::shutdown);
//    }
//
//    @Override
//    public void startup() {
//        log.info("startup()");
//
//        isLogsEnabled.set(false);
//        isMetricsEnabled.set(false);
//
//        persistenceManager.startup();
//
//        statusTagManager.startup();
//        closer.register(statusTagManager::shutdown);
//
//        wizardEMSManager.startup();
//        closer.register(wizardEMSManager::shutdown);
//
//        closer.register(snapshotsAgentManager::shutdown);
//        snapshotsAgentManager.startup();
//
//        updateModuleState(
//                persistenceManager
//                        .getRecordManagerSet()
//                        .getRedundant()
//                        .getOnlyRecord(MonitoringPublisherModuleRecord.RECORD_META));
//    }
//
//    @Override
//    public void shutdown() {
//        log.info("shutdown()");
//        stopLogManager();
//        stopTagManager();
//
//        try {
//            closer.close();
//        } catch (final IOException e) {
//            log.warn("Failure during shutdown.", e);
//        }
//    }
//
//    @Override
//    public void recordUpdated(@NonNull final MonitoringPublisherModuleRecord record) {
//        log.debug("Module record updated");
//        updateModuleState(record);
//        snapshotsAgentManager.modulesRecordUpdated(record);
//    }
//
//    private void updateModuleState(@NonNull final MonitoringPublisherModuleRecord record) {
//        log.debug("updateModuleState. record={}", RecordUtil.toJSON(record));
//        final boolean nextLogsEnabled = record.isLogsEnabled();
//        final boolean nextMetricsEnabled = record.isMetricsEnabled();
//        boolean logManagerError = false;
//        boolean metricsManagerError = false;
//        try {
//            while (true) {
//                if (isLogsEnabled.compareAndSet(nextLogsEnabled, nextLogsEnabled)) {
//                    if (nextLogsEnabled) {
//                        restartLogManager(record);
//                    }
//                    break;
//                } else if (nextLogsEnabled && isLogsEnabled.compareAndSet(false, true)) {
//                    restartLogManager(record);
//                    break;
//                } else if (!nextLogsEnabled && isLogsEnabled.compareAndSet(true, false)) {
//                    stopLogManager();
//                    break;
//                }
//            }
//        } catch (final LogManagerCreationException e) {
//            log.error(
//                    format(
//                            "Failed to start log manager. Disabling publishing logs. record=[%s]",
//                            RecordUtil.toJSON(record)),
//                    e);
//            logManagerError = true;
//        }
//        try {
//            while (true) {
//                if (isMetricsEnabled.compareAndSet(nextMetricsEnabled, nextMetricsEnabled)) {
//                    if (nextMetricsEnabled) {
//                        restartTagManager(record);
//                    }
//                    break;
//                } else if (nextMetricsEnabled && isMetricsEnabled.compareAndSet(false, true)) {
//                    restartTagManager(record);
//                    break;
//                } else if (!nextMetricsEnabled && isMetricsEnabled.compareAndSet(true, false)) {
//                    stopTagManager();
//                    break;
//                }
//            }
//        } catch (final TagManagerCreationException e) {
//            log.error(
//                    format(
//                            "Failed to start tag manager. Disabling publishing metrics. record=[%s]",
//                            RecordUtil.toJSON(record)),
//                    e);
//            metricsManagerError = true;
//        }
//        if (logManagerError || metricsManagerError) {
//            disableModuleOnError(record, logManagerError, metricsManagerError);
//        }
//    }
//
//    @Override
//    public void recordAdded(@NonNull final MonitoringPublisherModuleRecord record) {
//        throw new IllegalStateException("Not expected.");
//    }
//
//    @Override
//    public void recordDeleted(@NonNull final KeyValue keyValue) {
//        throw new IllegalStateException("Not expected.");
//    }
//
//    @Override
//    public RecordMeta<MonitoringPublisherModuleRecord> getRecordMeta() {
//        return MonitoringPublisherModuleRecord.RECORD_META;
//    }
//
//    @Override
//    public IRecordListener<MonitoringPublisherModuleRecord> getRecordListener() {
//        return this;
//    }
//
//    @Override
//    public void setupInitialPersistence(
//            @NonNull final SchemaUpdater schemaUpdater, @NonNull final RecordManagerSet recordManagerSet)
//            throws SQLException {
//        schemaUpdater.updatePersistentRecords(getRecordMeta());
//
//        // Required record for primary module settings
//        final RecordManager recordManager = recordManagerSet.getRedundant();
//        final MonitoringPublisherModuleRecord settingsRecord = recordManager.createNew(getRecordMeta());
//        settingsRecord.setId(0L); // always '0'
//        schemaUpdater.ensureRecordExists(settingsRecord);
//    }
//
//    private void restartLogManager(@NonNull final MonitoringPublisherModuleRecord record)
//            throws LogManagerCreationException {
//        synchronized (localStateLock) {
//            stopLogManager();
//            startLogManager(record);
//        }
//    }
//
//    private void restartTagManager(@NonNull final MonitoringPublisherModuleRecord record)
//            throws TagManagerCreationException {
//        synchronized (localStateLock) {
//            stopTagManager();
//            startTagManager(record);
//        }
//    }
//
//    private void startLogManager(@NonNull final MonitoringPublisherModuleRecord record)
//            throws LogManagerCreationException {
//        synchronized (localStateLock) {
//            try {
//                simpleLogManager = logManagerFactory.create(record);
//            } catch (final RuntimeException e) {
//                simpleLogManager = null;
//                throw new LogManagerCreationException(
//                        format("Failed to create log manager. record=[%s]", RecordUtil.toJSON(record)), e);
//            }
//        }
//    }
//
//    private void stopLogManager() {
//        synchronized (localStateLock) {
//            if (simpleLogManager != null) {
//                simpleLogManager.close();
//                simpleLogManager = null;
//            }
//        }
//    }
//
//    private void disableLogModule() {
//        final MonitoringPublisherModuleRecord record =
//                persistenceManager
//                        .getRecordManagerSet()
//                        .getRedundant()
//                        .getOnlyRecord(MonitoringPublisherModuleRecord.RECORD_META);
//        record.setLogsEnabled(false);
//        persistenceManager.getRecordManagerSet().getRedundant().update(record);
//    }
//
//    private void startTagManager(@NonNull final MonitoringPublisherModuleRecord record)
//            throws TagManagerCreationException {
//        synchronized (localStateLock) {
//            try {
//                tagSetManager.startup(record);
//            } catch (@NonNull final RuntimeException e) {
//                throw new TagManagerCreationException(
//                        format("Failed to start tag manager. record=[%s]", RecordUtil.toJSON(record)), e);
//            }
//        }
//    }
//
//    private void stopTagManager() {
//        synchronized (localStateLock) {
//            tagSetManager.shutdown();
//        }
//    }
//
//    private void disableMetricsModule() {
//        final MonitoringPublisherModuleRecord record =
//                persistenceManager
//                        .getRecordManagerSet()
//                        .getRedundant()
//                        .getOnlyRecord(MonitoringPublisherModuleRecord.RECORD_META);
//        record.setMetricsEnabled(false);
//        persistenceManager.getRecordManagerSet().getRedundant().update(record);
//    }
//
//    private void disableModuleOnError(
//            @NonNull final MonitoringPublisherModuleRecord record,
//            final boolean logManagerError,
//            boolean metricsManagerError) {
//        while (true) {
//            if (logManagerError && metricsManagerError) {
//                record.setLogsEnabled(false);
//                record.setMetricsEnabled(false);
//                persistenceManager.getRecordManagerSet().getRedundant().update(record);
//                break;
//            } else if (logManagerError) {
//                disableLogModule();
//                break;
//            } else if (metricsManagerError) {
//                disableMetricsModule();
//                break;
//            }
//        }
//    }
//
//    @SuppressWarnings("unused")
//    @lombok.Builder(builderClassName = "Builder", access = AccessLevel.PACKAGE)
//    private static GwManager create(
//            @NonNull final PersistenceManager persistenceManager,
//            @NonNull final BundleUtilHelper bundleUtilHelper,
//            @NonNull final SimpleLogManager.Factory logManagerFactory,
//            @NonNull final ModuleStateManager<SimpleModuleState> statusTagManager,
//            @NonNull final AtomicBoolean isLogsEnabled,
//            @NonNull final AtomicBoolean isMetricsEnabled,
//            @NonNull final Closer closer,
//            @NonNull final TagSetManager tagSetManager,
//            @NonNull final LogRecordManager logRecordManager,
//            @NonNull final WizardEMSManager wizardEMSManager,
//            @NonNull final ModuleStateManager<MetricsTagProviderUpdater> metricsRegistryTagManager,
//            @NonNull final MetricsManager metricsManager,
//            @NonNull final SnapshotsRecordManager snapshotsRecordManager,
//            @NonNull final SnapshotsAgentManager snapshotsAgentManager) {
//        return new GwManager(
//                persistenceManager,
//                bundleUtilHelper,
//                logManagerFactory,
//                statusTagManager,
//                isLogsEnabled,
//                isMetricsEnabled,
//                closer,
//                tagSetManager,
//                logRecordManager,
//                wizardEMSManager,
//                metricsRegistryTagManager,
//                metricsManager,
//                snapshotsAgentManager,
//                snapshotsRecordManager);
//    }
//}
//
//
//
