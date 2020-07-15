package metric;

import com.google.common.collect.Lists;
import com.inductiveautomation.ignition.common.QualifiedPathUtils;
import com.inductiveautomation.ignition.common.browsing.BrowseFilter;
import com.inductiveautomation.ignition.common.browsing.TagInfoResult;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.gateway.sqltags.distributed.TagProviderService;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 提取 tag provider 中的 tag 信息
 */
@Slf4j
public class TagsUtil{
    private TagsUtil() {
        throw new IllegalStateException();
    }

    public static List<NodeDescription> readAllTags(@NonNull String tagProviderName, @NonNull GatewayTagManager tagsManager, TagPath rootPath) throws IOException, InterruptedException, ExecutionException

    {
        if (tagProviderName == null) {
            throw new NullPointerException("tagProviderName is marked non-null but is null");
        } else if (tagsManager == null) {
            throw new NullPointerException("tagsManager is marked non-null but is null");
        } else {

//            TagPath rootPath = TagPathParser.parse(tagProviderName, "");
            log.warn("LLLLLLLLLLLLLLLLLLLL");
            TagProvider tagProvider = tagsManager.getTagProvider(tagProviderName);
            log.warn(tagProvider.getName());
            return null != tagProvider ? Lists.newArrayList(tagProvider.browseAsync(rootPath,  new BrowseFilter()).get().getResults()) : Lists.newArrayList();
        }
    }
}



