package com.inductiveautomation.ignition.examples.mtp;

import java.util.Random;

import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.model.values.QualityCode;
import com.inductiveautomation.ignition.common.sqltags.model.TagProviderMeta;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.managed.ManagedTagProvider;
import com.inductiveautomation.ignition.gateway.tags.managed.ProviderConfiguration;
import org.apache.log4j.LogManager;
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

    public ManagedProviderGatewayHook() {
        logger = LogManager.getLogger(this.getClass());
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
            logger.fatal("Error setting up ManagedTagProvider example module.", e);
        }
    }

    @Override
    public void startup(LicenseState activationState) {
        try {
            //Register a task with the execution system to update values every second.
            // 相当于让系统自己跑一个脚本，执行一个程序。而这里执行的是 updateValues，即更新数据
            // 这里的 1000 应该是指 1000 ms
            context.getExecutionManager().register(getClass().getName(), TASK_NAME, this::updateValues, 1000);

            logger.info("Example Provider module started.");
        } catch (Exception e) {
            logger.fatal("Error starting up ManagedTagProvider example module.", e);
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
            logger.error("Error stopping ManagedTagProvider example module.", e);
        }
        logger.info("ManagedTagProvider example module stopped.");
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
}
