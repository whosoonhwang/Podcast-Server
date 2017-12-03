package lan.dk.podcastserver.manager.worker.downloader.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lan.dk.podcastserver.entity.Item;
import lombok.Builder;
import lombok.Value;

/**
 * Created by kevin on 03/12/2017
 */
@Value
@Builder
public class DownloadingItem {
    private final Item item;
    private final List<String> urls;
    private final String filename;

    public Boolean hasOnlyOneUrl() {
        return urls.length() == 1;
    }

    public Option<String> url() {
        return urls.headOption();
    }
}
