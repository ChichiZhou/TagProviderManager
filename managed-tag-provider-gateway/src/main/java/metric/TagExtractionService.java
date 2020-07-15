//package metric;
//
//import static java.lang.String.format;
//
////import com.aftmi.ignitionutil.tags.TagsUtil;
//import com.amazon.aws.cloudwatch.extension.client.MetricAggregationCloudWatch;
//import com.amazonaws.services.cloudwatch.model.Dimension;
//import com.amazonaws.services.cloudwatch.model.MetricDatum;
//import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
//import com.google.common.base.Stopwatch;
//import com.google.common.collect.ImmutableList;
//import com.google.common.collect.Lists;
//import com.google.common.io.Closer;
//import com.inductiveautomation.ignition.common.browsing.TagInfoResult;
//import com.inductiveautomation.ignition.common.execution.ExecutionManager;
//import com.inductiveautomation.ignition.common.sqltags.model.TagInfo;
//import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
//import com.inductiveautomation.ignition.common.sqltags.model.types.ExtendedTagType;
//import com.inductiveautomation.ignition.common.sqltags.model.types.TagType;
//import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
//import com.inductiveautomation.ignition.common.tags.model.TagPath;
//import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
//import com.inductiveautomation.ignition.gateway.model.SystemPropertiesRecord;
//import com.inductiveautomation.ignition.gateway.redundancy.RedundancyManager;
//import com.inductiveautomation.ignition.gateway.redundancy.types.NodeRole;
//import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
//import java.io.IOException;
//import java.time.Duration;
//import java.util.List;
//import java.util.Locale;
//import java.util.Optional;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Function;
//import lombok.AccessLevel;
//import lombok.NonNull;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * 这部分的作用就是根据 tag provider 来提取 tag 信息，然后用 AWS API 将这些信息pulish
// */
//@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
//@Slf4j
//class TagExtractionService {
//    @NonNull private final String owner;
//    @NonNull private final String task;
//    @NonNull private final ExecutionManager executionManager;
//    @NonNull private final String tagProviderName;
//    @NonNull private final RedundancyManager redundancyManager;
//    @NonNull private final ImmutableList<Dimension> dimensionList;
//    @NonNull private final ImmutableList<Dimension> dimensionListWithNodeRole;
//    @NonNull private final Dimension activeNodeDimension;
//    private final boolean publishNodeRoleDimension;
//    @NonNull private final GatewayTagManager tagsManager;
//    @NonNull private final MetricAggregationCloudWatch client;
//    @NonNull private final Closer closer;
//
//
//
//    void startup() throws  IOException {
//
//        executionManager.registerAtFixedRate(
//                owner, task, this::readAndPublishTags, 1, TimeUnit.SECONDS);
//
//
//    }
//
//    void shutdown() {
//        final Stopwatch timer = Stopwatch.createStarted();
//        boolean isError = true;
//        try {
//            closer.close();
//            isError = false;
//        } catch (final IOException e) {
//            throw new IllegalStateException(
//                    format("Failed to shutdown tagExtraction service. tagProvider=[%s]", tagProviderName));
//        } finally {
//            log.info(
//                    "TagExtractionService shutdown. tagProvider=[{}], duration=[{}], isError=[{}]",
//                    tagProviderName,
//                    Duration.ofNanos(timer.elapsed(TimeUnit.NANOSECONDS)),
//                    isError);
//        }
//    }
//
//    // XBBSQJ
//    void readAndPublishTags(){
//        final List<MetricDatum> tagRecords = Lists.newArrayList();
//        try {
//            TagPath rootPath = TagPathParser.parse(tagProviderName, "");
//            TagsUtil.readAllTags(tagProviderName, tagsManager, rootPath)
//                    .forEach(
//                            nodeDescription -> {
//                                if (publishNodeRoleDimension) {
//                                    createMultipleTagRecord(
//                                            redundancyManager.getCurrentState().getActivityLevel().isHot(),
//                                            nodeDescription, rootPath)
//                                            .ifPresent(tagRecords::addAll);
//                                } else {
//                                    createTagRecord(
//                                            redundancyManager.getCurrentState().getActivityLevel().isHot(),
//                                            nodeDescription, rootPath)
//                                            .ifPresent(tagRecords::add);
//                                }
//                            });
//        } catch (final IOException e) {
//            // no-op. When the tagProvider comes up, it starts sending metrics
//        } catch (final InterruptedException e){
//            // no-op. When the tagProvider comes up, it starts sending metrics
//        } catch (final ExecutionException e){
//            // no-op. When the tagProvider comes up, it starts sending metrics
//        }
//
//        if (!tagRecords.isEmpty()) {
//            publish(tagRecords);
//        }
//    }
//
//    private Optional<MetricDatum> createTagRecord(
//            final boolean isHot, @NonNull final NodeDescription nodeDescription, TagPath rootPath) {
//        // final TagInfo tagInfo = tagInfoResult.getTagInfo();
//        // 这个是 AWS service 中的内容
//        final List<Dimension> dimensions = Lists.newArrayList(dimensionList);
//
//        dimensions.add(activeNodeDimension.withValue(Boolean.toString(isHot)));
//        return getTagRecordValueConverter(TagType.fromTagObjectType(nodeDescription.getObjectType()), nodeDescription.getDataType())
//                .map(
//                        converter ->
//                                new MetricDatum()
//                                        .withMetricName(rootPath.toString())
//                                        .withValues(converter.apply(nodeDescription.getCurrentValue().getValue()))
//                                        .withDimensions(dimensions));
//    }
//
//    private Optional<List<MetricDatum>> createMultipleTagRecord(
//            final boolean isHot, @NonNull final NodeDescription nodeDescription, TagPath rootPath) {
//        //final TagInfo tagInfo = tagInfoResult.getTagInfo();
//
//        final List<Dimension> activeNodeList = Lists.newArrayList(dimensionList);
//        final List<MetricDatum> metricData = Lists.newArrayList();
//
//        activeNodeList.add(activeNodeDimension.withValue(Boolean.toString(isHot)));
//        return getTagRecordValueConverter(TagType.fromTagObjectType(nodeDescription.getObjectType()), nodeDescription.getDataType())
//                .map(
//                        converter -> {
//                            metricData.add(
//                                    new MetricDatum()
//                                            .withMetricName(rootPath.toString())
//                                            .withValues(converter.apply(nodeDescription.getCurrentValue().getValue()))
//                                            .withDimensions(activeNodeList));
//                            metricData.add(
//                                    new MetricDatum()
//                                            .withMetricName(rootPath.toString())
//                                            .withValues(converter.apply(nodeDescription.getCurrentValue().getValue()))
//                                            .withDimensions(dimensionListWithNodeRole));
//                            return metricData;
//                        });
//    }
//
//    void publish(@NonNull final List<MetricDatum> data) {
//        final PutMetricDataRequest request =
//                new PutMetricDataRequest().withNamespace(Constants.TAGS_NAMESPACE).withMetricData(data);
//        client.putMetricDataForAggregation(request);
//    }
//
//    private static Optional<Function<Object, Double>> getTagRecordValueConverter(
//            @NonNull final ExtendedTagType tagType, @NonNull final DataType dataType) {
//
//        if (tagType.equals(TagType.Folder)) {
//            return Optional.empty();
//        }
//
//        switch (dataType) {
//            case Boolean:
//                return Optional.of(TagExtractionService::fromBoolean);
//            case Int1:
//            case Int2:
//            case Int4:
//            case Int8:
//            case Float4:
//            case Float8:
//                return Optional.of(TagExtractionService::fromNumber);
//            default:
//                return Optional.empty();
//        }
//    }
//
//    private static Double fromBoolean(final Object value) {
//        if (value != null) {
//            return Boolean.TRUE.equals(value) ? 1D : 0D;
//        }
//        return 0D;
//    }
//
//    private static Double fromNumber(final Object value) {
//        if (value != null && value instanceof Number) {
//            return ((Number) value).doubleValue();
//        }
//        return 0D;
//    }
//
//    @lombok.Builder(builderClassName = "Builder")
//    private static TagExtractionService create(
//            @NonNull final ExecutionManager executionManager,
//            @NonNull final SystemPropertiesRecord systemPropertiesRecord,
//            @NonNull final RedundancyManager redundancyManager,
//            @NonNull final NodeRole nodeRole,
//            @NonNull final String tagProviderName,
//            @NonNull final GatewayTagManager GatewayTagManager,
//            @NonNull final MetricAggregationCloudWatch client,
//            final boolean publishNodeRoleDimension) {
//        final Closer closer = Closer.create();
//        final String owner = TagExtractionService.class.getSimpleName();
//        final String task = format("tagsExtractor%s", tagProviderName);
//        final Dimension activeNodeDimension =
//                new Dimension().withName(Constants.ACTIVE_NODE_DIMENSION_KEY);
//
//        final ImmutableList dimensionList =
//                ImmutableList.<Dimension>builder()
//                        .add(
//                                new Dimension()
//                                        .withName(Constants.SYSTEM_NAME_DIMENSION_KEY)
//                                        .withValue(systemPropertiesRecord.getSystemName()))
//                        .add(
//                                new Dimension().withName(Constants.SOURCE_DIMENSION_KEY).withValue(tagProviderName))
//                        .build();
//
//        final ImmutableList nodeRoleDimensionList =
//                ImmutableList.<Dimension>builder()
//                        .add(
//                                new Dimension()
//                                        .withName(Constants.NODE_ROLE_DIMENSION_KEY)
//                                        .withValue(nodeRole.toString(Locale.getDefault())))
//                        .addAll(dimensionList)
//                        .build();
//
//        closer.register(() -> executionManager.unRegister(owner, task));
//        // client has to be shutdown after readTags has been unregistered
//        closer.register(client::shutdown);
//
//        return new TagExtractionService(
//                owner,
//                task,
//                executionManager,
//                tagProviderName,
//                redundancyManager,
//                dimensionList,
//                nodeRoleDimensionList,
//                activeNodeDimension,
//                publishNodeRoleDimension,
//                GatewayTagManager,
//                client,
//                closer);
//    }
//}
//
//
