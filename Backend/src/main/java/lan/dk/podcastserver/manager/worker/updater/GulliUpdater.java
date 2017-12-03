package lan.dk.podcastserver.manager.worker.updater;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lan.dk.podcastserver.entity.Cover;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.service.HtmlService;
import lan.dk.podcastserver.service.ImageService;
import lan.dk.podcastserver.service.SignatureService;
import lan.dk.podcastserver.service.properties.PodcastServerParameters;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import javax.validation.Validator;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

import static io.vavr.API.Option;
import static lan.dk.podcastserver.utils.MatcherExtractor.PatternExtractor;
import static lan.dk.podcastserver.utils.MatcherExtractor.from;

/**
 * Created by kevin on 05/10/2016 for Podcast Server
 */
@Slf4j
@Component("GulliUpdater")
public class GulliUpdater extends AbstractUpdater {

    private static final PatternExtractor FRAME_EXTRACTOR = from(Pattern.compile(".*\\.html\\(.*<iframe.* src=\"([^\"]*)\".*"));

    private final HtmlService htmlService;
    private final ImageService imageService;

    public GulliUpdater(PodcastServerParameters podcastServerParameters, SignatureService signatureService, Validator validator, HtmlService htmlService, ImageService imageService) {
        super(podcastServerParameters, signatureService, validator);
        this.htmlService = htmlService;
        this.imageService = imageService;
    }

    @Override
    public Set<Item> getItems(Podcast podcast) {
        return htmlService.get(podcast.getUrl())
                .map(d -> d.select("div.all-videos ul li.col-md-3"))
                .filter(Objects::nonNull)
                .map(this::asItemsSet)
                .getOrElse(HashSet::empty);
    }

    private Set<Item> asItemsSet(Elements elements) {
        return HashSet.ofAll(elements)
                .map(this::findDetailsInFromPage);
    }

    private Item findDetailsInFromPage(Element e) {
        return Option(e.select("a").first())
            .map(elem -> elem.attr("href"))
            .flatMap(htmlService::get)
            .map(d -> d.select(".bloc_streaming").first())
            .flatMap(this::htmlToItem)
            .map(i -> i.setCover(getCover(e)))
            .getOrElse(Item.DEFAULT_ITEM);
    }

    private Option<Item> htmlToItem(Element block) {
        return List.ofAll(block.select("script"))
                .find(e -> e.html().contains("iframe"))
                .map(Element::html)
                .map(FRAME_EXTRACTOR::on)
                .flatMap(m -> m.group(1))
                .map(url -> Item.builder()
                    .title(block.select(".episode_title").text())
                    .description(block.select(".description").text())
                    .url(url)
                    .pubDate(ZonedDateTime.now())
                .build());
    }

    private Cover getCover(Element block) {
        return Option(block)
                .map(e -> e.select("img").attr("src"))
                .map(imageService::getCoverFromURL)
                .getOrElse(Cover.DEFAULT_COVER);
    }

    @Override
    public String signatureOf(Podcast podcast) {
        return htmlService.get(podcast.getUrl())
                .map(d -> d.select("div.all-videos ul").first())
                .filter(Objects::nonNull)
                .map(Element::html)
                .map(signatureService::generateMD5Signature)
                .getOrElse(StringUtils.EMPTY);
    }

    @Override
    public Type type() {
        return new Type("Gulli", "Gulli");
    }

    @Override
    public Integer compatibility(String url) {
        return url.contains("replay.gulli.fr") ? 1 : Integer.MAX_VALUE;
    }
}
