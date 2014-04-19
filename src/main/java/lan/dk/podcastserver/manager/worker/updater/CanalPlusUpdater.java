package lan.dk.podcastserver.manager.worker.updater;

import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Podcast;
import lan.dk.podcastserver.utils.DateUtils;
import lan.dk.podcastserver.utils.DigestUtils;
import lan.dk.podcastserver.utils.ImageUtils;
import lan.dk.podcastserver.utils.jDomUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.JDOMException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("CanalPlusUpdater")
@Scope("prototype")
public class CanalPlusUpdater extends AbstractUpdater {

    public Podcast updateFeed(Podcast podcast) {

        Elements listingEpisodes = null;
        Document page = null;

        Set<Item> itemSet = null;

        try {
            page = Jsoup.connect(podcast.getUrl()).get();

            // Si la page possède un planifier :
            if (!page.select(".planifier .cursorPointer").isEmpty() && itemSet == null) { //Si c'est un lien direct vers la page de l'emmission, et donc le 1er Update
                itemSet = this.getSetItemToPodcastFromPlanifier(podcast.getUrl());
            }

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }

        // Si pas d'autre solution, ou que l'url ne contient pas front_tools:
        if (!podcast.getUrl().contains("front_tools") && (itemSet == null)) { //Si c'est un lien direct vers la page de l'emmission, et donc le 1er Update
            itemSet = this.getSetItemToPodcastFromFrontTools(
                    this.getPodcastURLFromFrontTools(podcast.getUrl())
            );
        }

        // Si l'url est une front-tools et que la liste est encore vide :
        if (podcast.getUrl().contains("front_tools") && (itemSet == null)) { //Si c'est un lien direct vers la page de l'emmission, et donc le 1er Update
            itemSet = this.getSetItemToPodcastFromFrontTools(podcast.getUrl());
        }

        if (itemSet != null) {
            for (Item item : itemSet) {
                if (!podcast.getItems().contains(item)) {

                    // Si le bean est valide :
                    item.setPodcast(podcast);
                    Set<ConstraintViolation<Item>> constraintViolations = validator.validate( item );
                    if (constraintViolations.isEmpty()) {
                        podcast.getItems().add(item);
                    } else {
                        logger.error(constraintViolations.toString());
                    }
                }
            }
        }


        //podcast.setRssFeed(jDomUtils.podcastToXMLGeneric(podcast, this.getServerURL()));

        logger.debug(podcast.toString());
        logger.debug("Nombre d'episode : " + podcast.getItems().size());
        //logger.debug(podcast.getRssFeed());


        return podcast;
    }

    public Podcast findPodcast(String url) {
        return null; // retourne un Podcast à partir de l'url fournie
    }

    @Override
    public String signaturePodcast(Podcast podcast) {
        Document page = null;

        try {
            page = Jsoup.connect(podcast.getUrl()).get();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }

        // Si la page possède un planifier :
        if (!page.select(".planifier .cursorPointer").isEmpty()) { //Si c'est un lien direct vers la page de l'emmission, et donc le 1er Update
            return DigestUtils.generateMD5SignatureFromDOM(page.select(".planifier .cursorPointer").html());
        }


        // Si pas d'autre solution, ou que l'url ne contient pas front_tools:
        if (!podcast.getUrl().contains("front_tools")) { //Si c'est un lien direct vers la page de l'emmission, et donc le 1er Update
            return DigestUtils.generateMD5SignatureFromUrl(this.getPodcastURLFromFrontTools(podcast.getUrl()));
        }

        // Si l'url est une front-tools et que la liste est encore vide :
        if (podcast.getUrl().contains("front_tools")) { //Si c'est un lien direct vers la page de l'emmission, et donc le 1er Update
            return DigestUtils.generateMD5SignatureFromUrl(podcast.getUrl());
        }
        return "";
    }


    /**
     * **** Partie spécifique au Front Tools ******
     */

    private String getPodcastURLFromFrontTools(String canalPlusDirectShowUrl) {
        if (canalPlusDirectShowUrl.contains("front_tools"))
            return canalPlusDirectShowUrl;

        int pid = 0;
        int ztid = 0;
        Document page = null;
        try {
            page = Jsoup.connect(canalPlusDirectShowUrl).get();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }

        Pattern p = Pattern.compile(
                "^loadVideoHistory\\('[0-9]*','[0-9]*','[0-9]*','([0-9]*)','([0-9]*)', '[0-9]*', '[^']*'\\);");
        logger.debug(page.select("a[onclick^=loadVideoHistory]").first().attr("onclick"));
        Matcher m = p.matcher(page.select("a[onclick^=loadVideoHistory]").first().attr("onclick"));

        if (m.find()) {
            pid = Integer.parseInt(m.group(1));
            ztid = Integer.parseInt(m.group(2));
        }
        return "http://www.canalplus.fr/lib/front_tools/ajax/wwwplus_live_onglet.php?pid=" + pid + "&ztid=" + ztid + "&nbPlusVideos0=1";
    }

    private Elements getHTMLListingEpisodeFromFrontTools(String canalPlusFrontToolsUrl) throws MalformedURLException {

        Document page = null;

        int nbPlusVideos = 0;

        Pattern p = Pattern.compile(".*nbPlusVideos([0-9])=1.*");
        Matcher m = p.matcher(canalPlusFrontToolsUrl);

        logger.debug("Parsing de l'url pour récupérer l'identifiant du tab");
        if (m.find()) {
            nbPlusVideos = Integer.parseInt(m.group(1));
            logger.debug("nbPlusVideos = " + nbPlusVideos);
        } else {
            throw new MalformedURLException("nbPlusVideos Introuvable pour le show " + canalPlusFrontToolsUrl);
        }

        try {
            page = Jsoup.connect(canalPlusFrontToolsUrl).get();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }
        return page.select("ul.features").get(nbPlusVideos).select("li");
    }

    private Set<Item> getSetItemToPodcastFromFrontTools(String urlFrontTools) {
        Set<Item> itemList = new LinkedHashSet<Item>();
        try {
            Integer idCanalPlusEpisode;
            for (Element episode : getHTMLListingEpisodeFromFrontTools(urlFrontTools)) {
                idCanalPlusEpisode = Integer.valueOf(episode.select("li._thumbs").first().id().replace("video_", ""));
                itemList.add(getItemFromVideoId(idCanalPlusEpisode));
            }
            return itemList;
        } catch (MalformedURLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("MalformedURLException :", e);
        }
        return itemList;
    }


    public Set<Item> getSetItemToPodcastFromPlanifier(String urlPodcast) {

        Set<Item> itemSet = new LinkedHashSet<Item>();
        Document page = null;
        try {
            page = Jsoup.connect(urlPodcast).get();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }
        //Pattern.compile("^loadVideoHistory\\('[0-9]*','[0-9]*','[0-9]*','([0-9]*)','([0-9]*)', '[0-9]*', '[^']*'\\);");
        Pattern idDuPodcastPatern = Pattern.compile(".*\\(([0-9]*).*\\);");
        Matcher matcher;
        Item itemToAdd;
        for (Element cursorPointer : page.select(".planifier .cursorPointer")) {
            matcher = idDuPodcastPatern.matcher(cursorPointer.attr("onclick"));
            if (matcher.find()) {
                logger.debug(cursorPointer.select("h3 a").text());
                itemToAdd = getItemFromVideoId(Integer.valueOf(matcher.group(1)))
                        .setTitle(cursorPointer.select("h3 a").text());
                itemSet.add(itemToAdd);
            }
        }
        return itemSet;
    }

    /**
     * Helper permettant de créer un item à partir d'un ID de Video Canal+
     *
     * @param idCanalPlusVideo
     * @return
     */
    private Item getItemFromVideoId(Integer idCanalPlusVideo) {
        Item currentEpisode = new Item();
        //currentEpisode.setTitle(episode.select("h4 a").first().text());
        org.jdom2.Document xmlAboutCurrentEpisode = null;
        try {
            xmlAboutCurrentEpisode = jDomUtils.jdom2Parse("http://service.canal-plus.com/video/rest/getVideos/cplus/" + idCanalPlusVideo);
        } catch (JDOMException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("JDOMException :", e);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }
        org.jdom2.Element xml_INFOS = xmlAboutCurrentEpisode.getRootElement().getChild("VIDEO").getChild("INFOS");
        org.jdom2.Element xml_MEDIA = xmlAboutCurrentEpisode.getRootElement().getChild("VIDEO").getChild("MEDIA");

        try {
            currentEpisode.setTitle(xml_INFOS.getChild("TITRAGE").getChildText("TITRE"))
                    .setPubdate(DateUtils.canalPlusDateToTimeStamp(xml_INFOS.getChild("PUBLICATION").getChildText("DATE"), xml_INFOS.getChild("PUBLICATION").getChildText("HEURE")))
                    .setCover(ImageUtils.getCoverFromURL(new URL(xml_MEDIA.getChild("IMAGES").getChildText("GRAND"))))
                    .setDescription(xml_INFOS.getChild("TITRAGE").getChildText("SOUS_TITRE"));

            if (xml_MEDIA.getChild("VIDEOS").getChildText("HLS") != null && StringUtils.isNotEmpty(xml_MEDIA.getChild("VIDEOS").getChildText("HLS"))) {
                currentEpisode.setUrl(this.getM3U8UrlFromCanalPlusService(xml_MEDIA.getChild("VIDEOS").getChildText("HLS")));
            } else {
                currentEpisode.setUrl(xml_MEDIA.getChild("VIDEOS").getChildText("HD"));
            }
            //currentEpisode.setDescription((xml_INFOS.getChildText("DESCRIPTION").equals("")) ? xml_INFOS.getChild("TITRAGE").getChildText("SOUS_TITRE") : xml_INFOS.getChildText("DESCRIPTION"));

//                    if (!podcast.getItems().contains(currentEpisode)) {
//                        currentEpisode.setPodcast(podcast);
//                        podcast.getItems().add(currentEpisode);
//                    }

            logger.debug(currentEpisode.toString());
        } catch (ParseException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("ParseException :", e);
        } catch (MalformedURLException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("MalformedURLException pour l'item d'id Canal+ {}", idCanalPlusVideo, e);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            logger.error("IOException :", e);
        }
        return currentEpisode;
    }

    private String getM3U8UrlFromCanalPlusService(String standardM3UURL) {
        String urlToReturn = null;

        if (standardM3UURL == null)
            return null;

        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new URL(standardM3UURL).openStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (!inputLine.startsWith("#")) {
                    urlToReturn = inputLine;
                }
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return urlToReturn;
    }
}