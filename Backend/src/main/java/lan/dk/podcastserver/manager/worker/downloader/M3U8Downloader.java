package lan.dk.podcastserver.manager.worker.downloader;


import io.vavr.control.Try;
import lan.dk.podcastserver.entity.Item;
import lan.dk.podcastserver.entity.Status;
import lan.dk.podcastserver.repository.ItemRepository;
import lan.dk.podcastserver.repository.PodcastRepository;
import lan.dk.podcastserver.service.*;
import lan.dk.podcastserver.service.properties.PodcastServerParameters;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.progress.ProgressListener;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Scope("prototype")
@Component("M3U8Downloader")
public class M3U8Downloader extends AbstractDownloader {

    protected final UrlService urlService;
    protected final M3U8Service m3U8Service;
    protected final FfmpegService ffmpegService;
    protected final ProcessService processService;

    protected Process process;

    public M3U8Downloader(ItemRepository itemRepository, PodcastRepository podcastRepository, PodcastServerParameters podcastServerParameters, SimpMessagingTemplate template, MimeTypeService mimeTypeService, UrlService urlService, M3U8Service m3U8Service, FfmpegService ffmpegService, ProcessService processService) {
        super(itemRepository, podcastRepository, podcastServerParameters, template, mimeTypeService);
        this.urlService = urlService;
        this.m3U8Service = m3U8Service;
        this.ffmpegService = ffmpegService;
        this.processService = processService;
    }

    @Override
    public Item download() {
        log.debug("Download");

        target = getTargetFile(item);

        Double duration = ffmpegService.getDurationOf(getItemUrl(item), withUserAgent());

        FFmpegBuilder command = new FFmpegBuilder()
                .setUserAgent(withUserAgent())
                .addInput(getItemUrl(item))
                .addOutput(target.toAbsolutePath().toString())
                .setFormat("mp4")
                .setAudioBitStreamFilter(FfmpegService.AUDIO_BITSTREAM_FILTER_AAC_ADTSTOASC)
                .setVideoCodec(FfmpegService.CODEC_COPY)
                .setAudioCodec(FfmpegService.CODEC_COPY)
                .done();


        process = ffmpegService.download(getItemUrl(item), command, handleProgression(0d, duration));

        processService.waitFor(process);

        if (item.getStatus() == Status.STARTED)
            finishDownload();

        return item;
    }

    protected String withUserAgent() {
        return UrlService.USER_AGENT_DESKTOP;
    }

    ProgressListener handleProgression(Double alreadyDoneDuration, Double globalDuration) {
        return p -> broadcastProgression(((Float) ((Long.valueOf(p.out_time_ms).floatValue() + alreadyDoneDuration.longValue()) / globalDuration.floatValue() * 100)).intValue());
    }

    void broadcastProgression(int cpt) {
        item.setProgression(cpt);
        log.debug("Progression : {}", item.getProgression());
        convertAndSaveBroadcast();
    }

    @Override
    public String getFileName(Item item) {
        return FilenameUtils.getBaseName(StringUtils.substringBeforeLast(getItemUrl(item), "?")).concat(".mp4");
    }

    @Override
    public void pauseDownload() {
        ProcessBuilder pauseProcess = processService.newProcessBuilder("kill", "-STOP", "" + processService.pidOf(process));
        processService
                .start(pauseProcess)
                .andThenTry(super::pauseDownload)
                .onFailure(e -> {
                    log.error("Error during pause of process :", e);
                    this.failDownload();
                });
    }

    @Override
    public void restartDownload() {
        ProcessBuilder restart = new ProcessBuilder("kill", "-SIGCONT", "" + processService.pidOf(process));

        processService.start(restart)
                .andThenTry(() -> {
                    item.setStatus(Status.STARTED);
                    saveSyncWithPodcast();
                    convertAndSaveBroadcast();
                })
                .onFailure(e -> {
                    log.error("Error during restart of process :", e);
                    this.failDownload();
                });
    }

    @Override
    public void stopDownload() {
        Try.run(() -> process.destroy());
        super.stopDownload();
    }

    @Override
    public Integer compatibility(String url) {
        return url.contains("m3u8") ? 10 : Integer.MAX_VALUE;
    }
}
