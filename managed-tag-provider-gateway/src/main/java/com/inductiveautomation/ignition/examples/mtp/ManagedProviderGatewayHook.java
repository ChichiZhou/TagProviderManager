package com.inductiveautomation.ignition.examples.mtp;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.google.common.collect.Lists;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.sqltags.model.types.ExtendedTagType;
import com.inductiveautomation.ignition.common.sqltags.model.types.TagType;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ProviderConfiguration;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import metric.Constants;
import metric.TagsUtil;
import org.apache.log4j.Logger;

/**
 * The "gateway hook" is the entry point for a module on the gateway. Since this example is so simple, we just do
 * everything here.
 * <p/>
 * This example uses the {@link ManagedTagProvider} to expose tags. We create a number of tags under a
 * folder, and update their values every second with random values.
 * <p/>
 * There is a "control" tag that can be used to modify the number of tags provided. This tag illustrates how to set up
 * write handling.
 *
 * 能否根据这个来模拟一个 tag 流。
 * 从最初始的状态到把 metric publish 到 cloudwatch， 然后创建 carnaval 之类的
 *
 * 这里会创建一个 control tag，其作用是控制所创建的 Custom Tags 的数量。相当于一个开关。是可读可写的
 * 并且会创建许多别的 tags，其命名格式为 Custom Tags/Tag %d。 这些 tags 会自动更新。但是只读的
 *
 */
@Slf4j
public class ManagedProviderGatewayHook extends AbstractGatewayModuleHook {
    private static final String TASK_NAME = "UpdateSampleValues";
    //This pattern will be used for tag names. So, tags will be created under the "Custom Tags" folder.
    private static final String TAG_NAME_PATTERN = "Custom Tags/Tag %d";
    //This is the name of our "control" tag. It will be in the root folder.
    private static final String CONTROL_TAG = "Tag Count";
    private Logger logger;
    private GatewayContext context;
    private ManagedTagProvider ourProvider;
    //This example adds/removes tags, so we'll track how many we currently have.
    private int currentTagCount = 0;
    private String tagProviderName = "Example";


    public ManagedProviderGatewayHook() {
//        logger = LogManager.getLogger(this.getClass());
    }

    @Override
    public void setup(GatewayContext context) {
        try {
            this.context = context;
            // 新创建的 Provider 名字就是 Example
            ProviderConfiguration configuration = new ProviderConfiguration("Example");

            // Needed to allow tag configuration to be editable. Comment this out to disable tag configuration editing.
            configuration.setAllowTagCustomization(true);
            configuration.setPersistTags(false);
            configuration.setPersistValues(false);
            configuration.setAttribute(TagProviderMeta.FLAG_HAS_OPCBROWSE, false);

            // 创建需要的 tag provider
            ourProvider = context.getTagManager().getOrCreateManagedProvider(configuration);
            //Set up the control tag.
            //1) Create the tag, and set its type.
            //2) Register the write handler, so the tag can be modified.
            ourProvider.configureTag(CONTROL_TAG, DataType.Int4);
            ourProvider.registerWriteHandler(CONTROL_TAG, (TagPath target, Object value) -> {
                Integer intVal = TypeUtilities.toInteger(value);
                //The adjustTags function will add/remove tags, AND update the current value of the control tag.
                adjustTags(intVal);
                return QualityCode.Good;
            });

            //Now set up our first batch of tags.
            // 创建10个 tags
            adjustTags(10);



        } catch (Exception e) {
            log.warn("Error setting up ManagedTagProvider example module.", e);
        }
    }

    @Override
    public void startup(LicenseState activationState) {
        try {
            // Register a task with the execution system to update values every second.
            // 相当于让系统自己跑一个脚本，执行一个程序。而这里执行的是 updateValues，即更新数据
            // 这里的 1000 应该是指 1000 ms
            // context.getExecutionManager() 得到的是 BasicExecutionEngine
            // 将后面的注册到 BasicExecutionEngine 中
            log.warn("FFFFFFFFFFFFFF");
            context.getExecutionManager().register(getClass().getName(), TASK_NAME, this::updateValues, 1000);
//            context.getExecutionManager().register(getClass().getName(), "T", this::readAndPublishTags, 1000);


            // 用下面的方法创建一个 ExecutionManager
            // ExecutionManager executionManager = context.createExecutionManager()

            log.info("Example Provider module started.");

            readAndPublishTags();
        } catch (Exception e) {
            log.warn("Error starting up ManagedTagProvider example module.", e);
        }

    }

    @Override
    public void shutdown() {
        //Clean up the things we've registered with the platform, namely, our provider type.
        try {
            if (context != null) {
                //Remove our value update task
                context.getExecutionManager().unRegister(getClass().getName(), TASK_NAME);
                //Shutdown our provider (and delete all data)
                ourProvider.shutdown(true);
            }
        } catch (Exception e) {
            log.error("Error stopping ManagedTagProvider example module.", e);
        }
        log.info("ManagedTagProvider example module stopped.");
    }

    /**
     * This function adds or removes tags to/from our custom provider. Notice that it is synchronized, since we are
     * updating the values asynchronously. If we weren't careful to synchronize the threading, it might happen that
     * right as we remove tags, they're added again implicitly, because the value update is happening at the same time.
     *
     * @param newCount
     */
    private synchronized void adjustTags(int newCount) {
        if (newCount > currentTagCount) {
            for (int i = currentTagCount; i < newCount; i++) {
                // 创建 Tags
                ourProvider.configureTag(String.format(TAG_NAME_PATTERN, i), DataType.Float8);
            }
        } else if (newCount < currentTagCount) {
            for (int i = currentTagCount; i > newCount; i--) {
                // 创建 Tags
                ourProvider.removeTag(String.format(TAG_NAME_PATTERN, i));
            }
        }
        //Update current count.
        currentTagCount = newCount;
        //Make sure to update the control tag with the current value.
        ourProvider.updateValue(CONTROL_TAG, currentTagCount, QualityCode.Good);
    }

    /**
     * Update the values of the tags.
     * 这个作用是每秒钟更新 tag value
     */
    private synchronized void updateValues() {
        Random r = new Random();
        for (int i = 0; i < currentTagCount; i++) {
            // 更新 Tags
            ourProvider.updateValue(String.format(TAG_NAME_PATTERN, i), r.nextFloat(), QualityCode.Good);
        }
    }

   private void readAndPublishTags(){
        log.warn("TTTTTTTTTTTTT");
       GatewayTagManager tagsManager = this.context.getTagManager();
        // 创建 dimensions 这里应该是有很多个 dimension
       Dimension dimension = new Dimension()
               .withName("TAGS_TEST")
               .withValue("TEST1");

       final List<MetricDatum> tagRecords = Lists.newArrayList();
       try {
           // 新创建的 tagprovider name 就是 Example
           TagPath rootPath = TagPathParser.parse(tagProviderName, "");
           List<NodeDescription> nodeDescriptions = TagsUtil.readAllTags(tagProviderName, tagsManager, rootPath);
           nodeDescriptions.forEach(description -> log.warn(description.getName()));

           TagsUtil.readAllTags(tagProviderName, tagsManager, rootPath)
                   .forEach(
                           nodeDescription -> {
                               createTagRecord(
                                       true,
                                       nodeDescription, rootPath, dimension)
                                       .ifPresent(tagRecords::add);
                           });
       } catch (final IOException e) {
           // no-op. When the tagProvider comes up, it starts sending metrics
       } catch (final InterruptedException e){
           // no-op. When the tagProvider comes up, it starts sending metrics
       } catch (final ExecutionException e){
           // no-op. When the tagProvider comes up, it starts sending metrics
       }

       if (!tagRecords.isEmpty()) {
           tagRecords.forEach(record -> log.warn(record.getMetricName()));
           //publish(tagRecords);
       }
   }

    private Optional<MetricDatum> createTagRecord(
            final boolean isHot, @NonNull final NodeDescription nodeDescription, TagPath rootPath, Dimension dimension) {
        // final TagInfo tagInfo = tagInfoResult.getTagInfo();
        // 这个是 AWS service 中的内容
        return getTagRecordValueConverter(TagType.fromTagObjectType(nodeDescription.getObjectType()), nodeDescription.getDataType())
                .map(
                        converter ->
                                new MetricDatum()
                                        .withMetricName(rootPath.toString())
                                        .withValue(converter.apply(nodeDescription.getCurrentValue().getValue()))
                                        .withDimensions(dimension));
    }

    private static Optional<Function<Object, Double>> getTagRecordValueConverter(
            @NonNull final ExtendedTagType tagType, @NonNull final DataType dataType) {

        if (tagType.equals(TagType.Folder)) {
            return Optional.empty();
        }

        switch (dataType) {
            case Boolean:
                return Optional.of(ManagedProviderGatewayHook::fromBoolean);
            case Int1:
            case Int2:
            case Int4:
            case Int8:
            case Float4:
            case Float8:
                return Optional.of(ManagedProviderGatewayHook::fromNumber);
            default:
                return Optional.empty();
        }
    }

    private static Double fromBoolean(final Object value) {
        if (value != null) {
            return Boolean.TRUE.equals(value) ? 1D : 0D;
        }
        return 0D;
    }

    private static Double fromNumber(final Object value) {
        if (value != null && value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0D;
    }

    void publish(@NonNull final List<MetricDatum> data) {
        final AmazonCloudWatch cw =
                AmazonCloudWatchClientBuilder.defaultClient();
        final PutMetricDataRequest request =
                new PutMetricDataRequest().withNamespace(Constants.TAGS_NAMESPACE).withMetricData(data);
        cw.putMetricData(request);
    }
}
