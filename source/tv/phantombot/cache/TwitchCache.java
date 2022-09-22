/* astyle --style=java --indent=spaces=4 --mode=java */

 /*
 * Copyright (C) 2016-2022 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

 /*
 * @author illusionaryone
 */
package tv.phantombot.cache;

import com.gmt2001.TwitchAPIv5;
import com.illusionaryone.ImgDownload;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import tv.phantombot.CaselessProperties;
import tv.phantombot.PhantomBot;
import tv.phantombot.event.EventBus;
import tv.phantombot.event.twitch.clip.TwitchClipEvent;
import tv.phantombot.event.twitch.gamechange.TwitchGameChangeEvent;
import tv.phantombot.event.twitch.offline.TwitchOfflineEvent;
import tv.phantombot.event.twitch.online.TwitchOnlineEvent;
import tv.phantombot.event.twitch.titlechange.TwitchTitleChangeEvent;
import tv.phantombot.twitch.api.Helix;

/**
 * TwitchCache Class
 *
 * This class keeps track of certain Twitch information such as if the channel is online or not and sends events to the JS side to indicate when the
 * channel has gone off or online.
 */
public final class TwitchCache {

    private static final TwitchCache INSTANCE = new TwitchCache();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    /* Cached data */
    private boolean isOnline = false;
    private boolean forcedGameTitleUpdate = false;
    private boolean forcedStreamTitleUpdate = false;
    private String streamCreatedAt = "";
    private String gameTitle = "Some Game";
    private String streamTitle = "Some Title";
    private String previewLink = "https://www.twitch.tv/p/assets/uploads/glitch_solo_750x422.png";
    private String logoLink = "https://www.twitch.tv/p/assets/uploads/glitch_solo_750x422.png";
    private ZonedDateTime streamUptime = null;
    private int viewerCount = 0;
    private int views = 0;
    private Instant offlineDelay = null;
    private Instant offlineTimeout = Instant.MIN;
    private ZonedDateTime latestClip = null;

    public static TwitchCache instance() {
        return INSTANCE;
    }

    /**
     * Constructor for TwitchCache object.
     *
     * @param channel Name of the Twitch Channel for which this object is created.
     */
    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    private TwitchCache() {
        this.executorService.schedule(this::startup, 1, TimeUnit.SECONDS);
    }

    private void startup() {
        if (PhantomBot.instance() != null && PhantomBot.instance().getDataStore() != null) {
            String gameTitlen = getDBString("game");
            String streamTitlen = getDBString("title");

            if (gameTitlen != null) {
                this.gameTitle = gameTitlen;
            }
            if (streamTitlen != null) {
                this.streamTitle = streamTitlen;
            }
            this.executorService.scheduleAtFixedRate(() -> {
                Thread.setDefaultUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());
                Thread.currentThread().setName("TwitchCache::updateCache");
                this.updateCache();
            }, 30, 30, TimeUnit.SECONDS);
            this.executorService.scheduleAtFixedRate(() -> {
                Thread.setDefaultUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());
                Thread.currentThread().setName("TwitchCache::updateClips");
                this.updateClips();
            }, 1, 1, TimeUnit.MINUTES);
        } else {
            this.executorService.schedule(this::startup, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * Polls the Clips endpoint, trying to find the most recent clip. Note that because Twitch reports by the viewcount, and has a limit of 100 clips,
     * it is possible to miss the most recent clip until it has views.
     *
     * We do not throw an exception because this is not a critical function unlike the gathering of data via the updateCache() method.
     */
    private void updateClips() {
        if (this.latestClip == null) {
            String lastDateStr = getDBString("last_clips_tracking_date");
            if (lastDateStr != null && !lastDateStr.isBlank()) {
                this.latestClip = ZonedDateTime.parse(lastDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } else {
                this.latestClip = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
            }
        }

        ZonedDateTime start = this.latestClip;
        Duration timeSinceLast = Duration.between(start, Instant.now());
        if (!timeSinceLast.abs().minusDays(1).isNegative()) {
            start = ZonedDateTime.ofInstant(Instant.now().minus(1, ChronoUnit.DAYS), ZoneId.systemDefault());
        }

        start = start.withSecond(0);

        Helix.instance().getClipsAsync(null, UsernameCache.instance().getIDCaster(), null, 100, null, null, start, start.plusDays(1))
                .doOnNext(jso -> {
                    if (jso != null && !jso.has("error") && jso.has("data") && !jso.isNull("data")) {
                        ZonedDateTime newLatestClip = this.latestClip;
                        String newLatestClipURL = null;
                        final JSONArray data = jso.getJSONArray("data");

                        if (data.length() > 0) {
                            for (int i = 0; i < data.length(); i++) {
                                JSONObject clip = data.getJSONObject(i);

                                try {
                                    if (i == 0) {
                                        this.setDBString("most_viewed_clip_url", clip.getString("url"));
                                    }

                                    ZonedDateTime clipDate = ZonedDateTime.parse(clip.getString("created_at"), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                                    if (clipDate.isAfter(this.latestClip)) {
                                        if (clipDate.isAfter(newLatestClip)) {
                                            newLatestClip = clipDate;
                                            newLatestClipURL = clip.getString("url");
                                        }

                                        JSONObject thumbnails = new JSONObject();
                                        thumbnails.put("medium", clip.getString("thumbnail_url"));
                                        thumbnails.put("small", clip.getString("thumbnail_url"));
                                        thumbnails.put("tiny", clip.getString("thumbnail_url"));

                                        EventBus.instance().postAsync(new TwitchClipEvent(clip.getString("url"), clip.getString("creator_name"),
                                                clip.getString("title"), thumbnails));
                                    }
                                } catch (NullPointerException | JSONException | DateTimeParseException ex) {
                                }
                            }

                            if (newLatestClipURL != null) {
                                this.latestClip = newLatestClip;
                                setDBString("last_clips_tracking_date", newLatestClip.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                                setDBString("last_clip_url", newLatestClipURL);
                            }
                        }
                    }
                }).doOnError(ex -> com.gmt2001.Console.err.printStackTrace(ex)).subscribe();
    }

    /**
     * Polls the Twitch API and updates the database cache with information. This method also sends events when appropriate.
     */
    private void updateCache() throws Exception {
        boolean success = true;
        boolean sentTwitchOnlineEvent = false;

        com.gmt2001.Console.debug.println("TwitchCache::updateCache");

        /* Retrieve Stream Information */
        try {
            JSONObject streamObj = TwitchAPIv5.instance().GetStream(this.channel);

            if (streamObj.getBoolean("_success") && streamObj.getInt("_http") == 200) {

                /* Determine if the stream is online or not */
                boolean isOnlinen = !streamObj.isNull("stream");

                if (!this.isOnline && isOnlinen) {
                    if (Instant.now().isAfter(this.offlineTimeout)) {
                        this.isOnline = true;
                        this.offlineDelay = null;
                        EventBus.instance().postAsync(new TwitchOnlineEvent());
                        sentTwitchOnlineEvent = true;
                    }
                } else if (this.isOnline && !isOnlinen) {
                    if (this.offlineDelay == null) {
                        /**
                         * @botproperty offlinedelay - The delay, in seconds, before the `channel` is confirmed to be offline. Default `30`
                         */
                        this.offlineDelay = Instant.now().plusSeconds(CaselessProperties.instance().getPropertyAsInt("offlinedelay", 30));
                    } else if (Instant.now().isAfter(this.offlineDelay)) {
                        this.offlineDelay = null;
                        this.isOnline = false;
                        /**
                         * @botproperty offlinetimeout - The timeout, in seconds, after `channel` goes offline before it can be online. Default `300`
                         */
                        this.offlineTimeout = Instant.now().plusSeconds(CaselessProperties.instance().getPropertyAsInt("offlinetimeout", 300));
                        EventBus.instance().postAsync(new TwitchOfflineEvent());
                    }
                }

                if (this.isOnline && isOnlinen) {
                    /* Calculate the stream uptime in seconds. */
                    try {
                        this.streamUptime = ZonedDateTime.parse(streamObj.getJSONObject("stream").getString("created_at"), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        this.streamCreatedAt = streamObj.getJSONObject("stream").getString("created_at");
                    } catch (DateTimeParseException | JSONException ex) {
                        success = false;
                        com.gmt2001.Console.err.println("TwitchCache::updateCache: Bad date from Twitch, cannot convert for stream uptime (" + streamObj.getJSONObject("stream").getString("created_at") + ")");
                        com.gmt2001.Console.err.printStackTrace(ex);
                    }

                    /* Determine the preview link. */
                    this.previewLink = streamObj.getJSONObject("stream").getJSONObject("preview").getString("template").replace("{width}", "1920").replace("{height}", "1080");

                    /* Get the viewer count. */
                    this.viewerCount = streamObj.getJSONObject("stream").getInt("viewers");
                } else if (!this.isOnline && !isOnlinen) {
                    this.streamUptime = null;
                    this.previewLink = "https://www.twitch.tv/p/assets/uploads/glitch_solo_750x422.png";
                    this.streamCreatedAt = "";
                    this.viewerCount = 0;
                }
            } else {
                success = false;
                if (streamObj.has("message")) {
                    com.gmt2001.Console.err.println("TwitchCache::updateCache: " + streamObj.getString("message"));
                } else {
                    com.gmt2001.Console.debug.println("TwitchCache::updateCache: Failed to update.");
                    com.gmt2001.Console.debug.println(streamObj.toString());
                }
            }
        } catch (JSONException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
            success = false;
        }

        // Wait a bit here.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            com.gmt2001.Console.debug.println(ex);
        }

        try {
            JSONObject streamObj = TwitchAPIv5.instance().GetChannel(this.channel);

            if (streamObj.getBoolean("_success") && streamObj.getInt("_http") == 200) {

                /* Get the game being streamed. */
                if (streamObj.has("game")) {
                    if (!streamObj.isNull("game")) {
                        String gameTitlen = streamObj.getString("game");
                        if (!forcedGameTitleUpdate && !this.gameTitle.equals(gameTitlen)) {
                            setDBString("game", gameTitlen);
                            /* Send an event if we did not just send a TwitchOnlineEvent. */
                            if (!sentTwitchOnlineEvent) {
                                this.gameTitle = gameTitlen;
                                EventBus.instance().postAsync(new TwitchGameChangeEvent(gameTitlen));
                            }
                            this.gameTitle = gameTitlen;
                        }

                        if (forcedGameTitleUpdate && this.gameTitle.equals(gameTitlen)) {
                            forcedGameTitleUpdate = false;
                        }
                    }
                } else {
                    success = false;
                }

                if (streamObj.has("views")) {
                    /* Get the view count. */
                    this.views = streamObj.getInt("views");
                }


                /* Get the logo */
                if (streamObj.has("logo") && !streamObj.isNull("logo")) {
                    String logoLinkn = streamObj.getString("logo");
                    this.logoLink = logoLinkn;
                    if (new File("./web/panel").isDirectory()) {
                        ImgDownload.downloadHTTPTo(logoLinkn, "./web/panel/img/logo.jpeg");
                    }
                }

                /* Get the title. */
                if (streamObj.has("status")) {
                    if (!streamObj.isNull("status")) {
                        String streamTitlen = streamObj.getString("status");

                        if (!forcedStreamTitleUpdate && !this.streamTitle.equals(streamTitlen)) {
                            setDBString("title", streamTitlen);
                            this.streamTitle = streamTitlen;
                            /* Send an event if we did not just send a TwitchOnlineEvent. */
                            if (!sentTwitchOnlineEvent) {
                                this.streamTitle = streamTitlen;
                                EventBus.instance().postAsync(new TwitchTitleChangeEvent(streamTitlen));
                            }
                            this.streamTitle = streamTitlen;
                        }
                        if (forcedStreamTitleUpdate && this.streamTitle.equals(streamTitlen)) {
                            forcedStreamTitleUpdate = false;
                        }
                    }
                } else {
                    success = false;
                }
            } else {
                success = false;
                if (streamObj.has("message")) {
                    com.gmt2001.Console.err.println("TwitchCache::updateCache: " + streamObj.getString("message"));
                } else {
                    com.gmt2001.Console.debug.println("TwitchCache::updateCache: Failed to update.");
                }
            }
        } catch (IOException | JSONException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
            success = false;
        }

        // Wait a bit here.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            com.gmt2001.Console.debug.println(ex);
        }

        if (PhantomBot.twitchCacheReady.equals("false") && success) {
            com.gmt2001.Console.debug.println("TwitchCache::setTwitchCacheReady(true)");
            PhantomBot.instance().setTwitchCacheReady("true");
        }
    }

    /**
     * Returns if the channel is online or not.
     *
     * @return
     */
    public boolean isStreamOnline() {
        return this.isOnline;
    }

    /**
     * Returns a String representation of true/false to indicate if the stream is online or not.
     *
     * @return
     */
    public String isStreamOnlineString() {
        if (this.isStreamOnline()) {
            return "true";
        }
        return "false";
    }

    /**
     * Returns the uptime of the channel in seconds.
     *
     * @return
     */
    public long getStreamUptimeSeconds() {
        if (this.streamUptime == null) {
            return 0L;
        }

        return Duration.between(this.streamUptime, ZonedDateTime.now()).getSeconds();
    }

    /**
     * Returns the stream created_at date from Twitch.
     *
     * @return
     */
    public String getStreamCreatedAt() {
        return this.streamCreatedAt;
    }

    /**
     * Returns the name of the game being played in the channel.
     *
     * @return
     */
    public String getGameTitle() {
        return this.gameTitle;
    }

    /**
     * Sets the game title.Useful for when !game is used.
     *
     * @param gameTitle
     */
    public void setGameTitle(String gameTitle) {
        this.setGameTitle(gameTitle, true);
    }

    /**
     * Sets the game title.Useful for when !game is used.
     *
     * @param gameTitle
     * @param sendEvent
     */
    public void setGameTitle(String gameTitle, boolean sendEvent) {
        boolean changed = !this.gameTitle.equals(gameTitle);
        setDBString("game", gameTitle);
        forcedGameTitleUpdate = true;
        this.gameTitle = gameTitle;
        if (changed && sendEvent) {
            EventBus.instance().postAsync(new TwitchGameChangeEvent(gameTitle));
        }
    }

    /**
     * Returns the title (status) of the stream.
     *
     * @return
     */
    public String getStreamStatus() {
        return this.streamTitle;
    }

    /**
     * Sets the title (status) of the stream.Useful for when !title is used.
     *
     * @param streamTitle
     */
    public void setStreamStatus(String streamTitle) {
        this.setStreamStatus(streamTitle, true);
    }

    /**
     * Sets the title (status) of the stream.Useful for when !title is used.
     *
     * @param streamTitle
     * @param sendEvent
     */
    public void setStreamStatus(String streamTitle, boolean sendEvent) {
        boolean changed = !this.streamTitle.equals(streamTitle);
        setDBString("title", streamTitle);
        forcedStreamTitleUpdate = true;
        this.streamTitle = streamTitle;
        if (changed && sendEvent) {
            EventBus.instance().postAsync(new TwitchTitleChangeEvent(streamTitle));
        }
    }

    /**
     * Returns the display name of the streamer.
     *
     * @return
     */
    public String getDisplayName() {
        return UsernameCache.instance().resolveCaster();
    }

    /**
     * Returns the preview link.
     *
     * @return
     */
    public String getPreviewLink() {
        return this.previewLink;
    }

    /**
     * Returns the logo link.
     *
     * @return
     */
    public String getLogoLink() {
        return this.logoLink;
    }

    /**
     * Returns the viewer count.
     *
     * @return
     */
    public int getViewerCount() {
        return this.viewerCount;
    }

    /**
     * Returns the views count.
     *
     * @return
     */
    public int getViews() {
        return this.views;
    }

    /**
     * Destroys the current instance of the TwitchCache object.
     */
    public void kill() {
        this.executorService.shutdown();
    }

    /**
     * Gets a string from the database. Simply a wrapper around the PhantomBot instance.
     *
     * @param String The database key to search for in the streamInfo table.
     * @return String Returns the found value or null.
     */
    private String getDBString(String dbKey) {
        return PhantomBot.instance().getDataStore().GetString("streamInfo", "", dbKey);
    }

    /**
     * Sets a string into the database. Simply a wrapper around the PhantomBot instance.
     *
     * @param String The database key to use for inserting the value into the streamInfo table.
     * @param String The value to insert.
     * @return String Returns the found value or null.
     */
    private void setDBString(String dbKey, String dbValue) {
        PhantomBot.instance().getDataStore().SetString("streamInfo", "", dbKey, dbValue);
    }

    /**
     * Sets online state
     *
     * @param shouldSendEvent
     */
    public void goOnline(boolean shouldSendEvent) {
        if (!this.isOnline && Instant.now().isAfter(this.offlineTimeout)) {
            this.isOnline = true;
            this.streamUptime = ZonedDateTime.now();

            if (shouldSendEvent) {
                EventBus.instance().postAsync(new TwitchOnlineEvent());
            }
        }
    }

    /**
     * Sets offline state
     *
     * @param shouldSendEvent
     */
    public void goOffline(boolean shouldSendEvent) {
        if (this.isOnline) {
            this.isOnline = false;
            this.viewerCount = 0;
            this.streamUptime = null;
            this.offlineTimeout = Instant.now().plusSeconds(CaselessProperties.instance().getPropertyAsInt("offlinetimeout", 300));

            if (shouldSendEvent) {
                EventBus.instance().postAsync(new TwitchOfflineEvent());
            }
        }
    }

    /**
     * Updates the stream status
     */
    public void syncStreamStatus() {
        this.syncStreamStatus(false);
    }

    /**
     * Updates the stream status
     *
     * @param isFromPubSubEvent
     */
    public void syncStreamStatus(boolean isFromPubSubEvent) {
        String userid = UsernameCache.instance().getIDCaster();
        List<String> userids = List.of(userid);
        Helix.instance().getStreamsAsync(20, null, null, userids, null, null, null).doOnSuccess(streams -> {
            if (streams != null && streams.has("data")) {
                if (streams.getJSONArray("data").length() > 0) {
                    JSONObject stream = streams.getJSONArray("data").getJSONObject(0);
                    boolean sendingOnline = false;

                    if (!isFromPubSubEvent) {
                        if (!this.isOnline) {
                            this.goOnline(true);
                            this.offlineDelay = null;
                            sendingOnline = true;
                        }
                    }

                    this.setStreamStatus(stream.optString("title", ""), !isFromPubSubEvent && !sendingOnline);
                    this.setGameTitle(stream.optString("game_name", ""), !isFromPubSubEvent && !sendingOnline);
                    this.updateViewerCount(stream.optInt("viewer_count", 0));
                    this.streamCreatedAt = stream.optString("started_at", "");
                    this.previewLink = stream.optString("thumbnail_url", "").replace("{width}", "1920").replace("{height}", "1080");
                } else {
                    if (!isFromPubSubEvent) {
                        if (this.isOnline) {
                            this.goOffline(true);
                            this.offlineDelay = null;
                        }
                    }
                }
            }
        }).subscribe();
    }

    /**
     * Updates the viewer count
     *
     * @param viewers
     */
    public void updateViewerCount(int viewers) {
        this.viewerCount = viewers;
    }

    /**
     * Updates the current game
     */
    public void updateGame() {
        this.updateGame(false);
    }

    /**
     * Updates the current game
     *
     * @param sendEvent
     */
    public void updateGame(boolean sendEvent) {
        JSONObject channelInfo = Helix.instance().getChannelInformationAsync(UsernameCache.instance().getIDCaster()).block();

        if (channelInfo != null && channelInfo.has("data") && channelInfo.getJSONArray("data").length() > 0) {
            this.setGameTitle(channelInfo.getJSONArray("data").getJSONObject(0).optString("game_name"), sendEvent);
        }
    }
}
