/*
 * Copyright (C) 2016-2021 phantombot.github.io/PhantomBot
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
package com.gmt2001;

import com.gmt2001.datastore.DataStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import tv.phantombot.PhantomBot;
import tv.phantombot.cache.UsernameCache;
import tv.phantombot.twitch.api.Helix;

/**
 * Stubs to @see Helix for backwards compatibility
 *
 * @author gmt2001
 * @author illusionaryone
 */
public class TwitchAPIv5 {

    private static final TwitchAPIv5 instance = new TwitchAPIv5();

    public static TwitchAPIv5 instance() {
        return instance;
    }

    private TwitchAPIv5() {
        Thread.setDefaultUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());
    }

    /**
     * Determines the ID of a username, if this fails it returns "0".
     *
     * @param channel
     * @return
     */
    private String getIDFromChannel(String channel) {
        return UsernameCache.instance().getID(channel);
    }

    public void SetClientID(String clientid) {
    }

    public void SetOAuth(String oauth) {
    }

    public boolean HasOAuth() {
        return true;
    }

    /**
     * Gets a channel object
     *
     * @param channel
     * @return
     */
    public JSONObject GetChannel(String channel) throws JSONException {
        List<String> user_id = new ArrayList<>();
        user_id.add(this.getIDFromChannel(channel));
        return this.translateGetChannel(Helix.instance().getChannelInformation(this.getIDFromChannel(channel)), Helix.instance().getUsers(user_id, null));
    }

    private JSONObject translateGetChannel(JSONObject channelData, JSONObject userData) {
        JSONObject result = new JSONObject();
        channelData = channelData.getJSONArray("data").getJSONObject(0);
        userData = userData.getJSONArray("data").getJSONObject(0);

        result.put("_id", Long.parseLong(channelData.getString("broadcaster_id")));
        result.put("broadcaster_language", channelData.getString("broadcaster_language"));
        result.put("created_at", userData.getString("created_at"));
        result.put("display_name", channelData.getString("broadcaster_name"));
        result.put("followers", 0);
        result.put("game", channelData.getString("game_name"));
        result.put("language", channelData.getString("broadcaster_language"));
        result.put("logo", userData.getString("profile_image_url"));
        result.put("mature", false);
        result.put("name", channelData.getString("broadcaster_login"));
        result.put("partner", userData.getString("broadcaster_type").equals("partner"));
        result.put("profile_banner", "");
        result.put("profile_banner_background_color", "");
        result.put("status", channelData.getString("title"));
        result.put("updated_at", "");
        result.put("url", "https://www.twitch.tv/" + channelData.getString("broadcaster_login"));
        result.put("video_banner", "");
        result.put("views", channelData.getLong("view_count"));

        return result;
    }

    /**
     * Updates the status and game of a channel
     *
     * @param channel
     * @param status
     * @param game
     * @return
     */
    public JSONObject UpdateChannel(String channel, String status, String game) throws JSONException {
        return UpdateChannel(channel, status, game, -1);
    }

    public JSONObject UpdateChannel(String channel, String oauth, String status, String game) throws JSONException {
        return UpdateChannel(channel, status, game, -1);
    }

    public JSONObject UpdateChannel(String channel, String oauth, String status, String game, int delay) throws JSONException {
        return UpdateChannel(channel, status, game, delay);
    }

    /**
     * Updates the status and game of a channel
     *
     * @param channel
     * @param status
     * @param game
     * @param delay -1 to not update
     * @return
     */
    public JSONObject UpdateChannel(String channel, String status, String game, int delay) throws JSONException {
        String gn = null;
        if (!game.isEmpty()) {
            JSONObject g = SearchGame(game);

            if (g.getBoolean("_success")) {
                if (g.getInt("_http") == 200) {
                    JSONArray a = g.getJSONArray("games");

                    if (a.length() > 0) {
                        boolean found = false;

                        for (int i = 0; i < a.length() && !found; i++) {
                            JSONObject o = a.getJSONObject(i);

                            gn = o.getString("id");

                            if (gn.equalsIgnoreCase(game)) {
                                found = true;
                            }
                        }

                        if (!found) {
                            JSONObject o = a.getJSONObject(0);

                            gn = o.getString("id");
                        }
                    }
                }
            }
        }

        return Helix.instance().updateChannelInformation(this.getIDFromChannel(channel), gn, null, status, delay);
    }

    /*
     * Updates the channel communities.
     */
    public JSONObject UpdateCommunities(String channel, String[] communities) throws JSONException {
        throw new UnsupportedOperationException("removed by Twitch");
    }

    /*
     * Searches for a game.
     */
    public JSONObject SearchGame(String game) throws JSONException {
        return this.translateSearchGame(Helix.instance().searchCategories(game, 20, null));
    }

    private JSONObject translateSearchGame(JSONObject gameData) {
        JSONObject result = new JSONObject();
        JSONArray games = new JSONArray();

        for (int i = 0; i < gameData.getJSONArray("data").length(); i++) {
            JSONObject data = gameData.getJSONArray("data").getJSONObject(i);
            String logo = data.getString("box_art_url");

            JSONObject boxart = new JSONObject();
            boxart.put("template", logo);
            boxart.put("large", logo.replaceAll("\\{width\\}", "272").replaceAll("\\{height\\}", "380"));
            boxart.put("medium", logo.replaceAll("\\{width\\}", "136").replaceAll("\\{height\\}", "190"));
            boxart.put("small", logo.replaceAll("\\{width\\}", "52").replaceAll("\\{height\\}", "72"));

            JSONObject logos = new JSONObject();
            logos.put("template", logo);
            logos.put("large", logo.replaceAll("\\{width\\}", "240").replaceAll("\\{height\\}", "144"));
            logos.put("medium", logo.replaceAll("\\{width\\}", "120").replaceAll("\\{height\\}", "72"));
            logos.put("small", logo.replaceAll("\\{width\\}", "60").replaceAll("\\{height\\}", "36"));

            JSONObject game = new JSONObject();
            game.put("_id", Long.parseLong(data.getString("id")));
            game.put("box", boxart);
            game.put("giantbomb_id", 0);
            game.put("logo", logos);
            game.put("name", data.getString("name"));

            games.put(game);
        }

        result.put("games", games);
        return result;
    }

    /*
     * Gets a communities id.
     */
    public JSONObject GetCommunityID(String name) throws JSONException {
        throw new UnsupportedOperationException("removed by Twitch");
    }

    /**
     * Gets an object listing the users following a channel
     *
     * @param channel
     * @param limit between 1 and 100
     * @param offset
     * @param ascending
     * @return
     */
    public JSONObject GetChannelFollows(String channel, int limit, int offset, boolean ascending) throws JSONException {
        if (offset > 0) {
            com.gmt2001.Console.warn.println("The offset parameter in is no longer supported, please update to use pagination from Helix");
        }

        if (ascending) {
            com.gmt2001.Console.warn.println("Sorting in ascending order is no longer supported");
        }

        return this.translateGetChannelFollows(Helix.instance().getUsersFollows(null, this.getIDFromChannel(channel), limit, null));
    }

    private JSONObject translateGetChannelFollows(JSONObject followData) {
        JSONObject result = new JSONObject();
        JSONArray follows = new JSONArray();

        result.put("_total", followData.getLong("total"));
        result.put("_cursor", followData.getJSONObject("pagination").getString("cursor"));

        for (int i = 0; i < followData.getJSONArray("data").length(); i++) {
            JSONObject data = followData.getJSONArray("data").getJSONObject(i);
            JSONObject follow = new JSONObject();
            follow.put("created_at", data.getString("followed_at"));
            follow.put("notifications", false);

            JSONObject user = new JSONObject();
            user.put("display_name", data.getString("from_name"));
            user.put("_id", data.getString("from_id"));
            user.put("name", data.getString("from_login"));
            user.put("type", "user");
            user.put("bio", "");
            user.put("created_at", "");
            user.put("updated_at", "");
            user.put("logo", "");

            follow.put("user", user);
            follows.put(follow);
        }

        result.put("follows", follows);
        return result;
    }

    /**
     * Gets an object listing the users subscribing to a channel
     *
     * @param channel
     * @param limit between 1 and 100
     * @param offset
     * @param ascending
     * @return
     */
    public JSONObject GetChannelSubscriptions(String channel, int limit, int offset, boolean ascending) throws JSONException {
        if (offset > 0) {
            com.gmt2001.Console.warn.println("The offset parameter in is no longer supported, please update to use pagination from Helix");
        }

        if (ascending) {
            com.gmt2001.Console.warn.println("Sorting in ascending order is no longer supported");
        }

        return this.translateGetChannelSubscriptions(Helix.instance().getBroadcasterSubscriptions(this.getIDFromChannel(channel), null, limit, null));
    }

    private JSONObject translateGetChannelSubscriptions(JSONObject subscriptionData) {
        JSONObject result = new JSONObject();
        JSONArray subscriptions = new JSONArray();
        Date now = new Date();

        result.put("_total", subscriptionData.getLong("total"));

        for (int i = 0; i < subscriptionData.getJSONArray("data").length(); i++) {
            JSONObject data = subscriptionData.getJSONArray("data").getJSONObject(i);
            JSONObject subscription = new JSONObject();
            subscription.put("_id", "");
            subscription.put("created_at", now);
            subscription.put("sub_plan", data.get("tier"));
            subscription.put("sub_plan_name", data.get("plan_name"));

            JSONObject user = new JSONObject();
            user.put("display_name", data.getString("user_name"));
            user.put("_id", data.getString("user_id"));
            user.put("name", data.getString("user_login"));
            user.put("type", "user");
            user.put("bio", "");
            user.put("created_at", "");
            user.put("updated_at", "");
            user.put("logo", "");

            subscription.put("user", user);
            subscriptions.put(subscription);
        }

        result.put("subscriptions", subscriptions);
        return result;
    }

    public JSONObject GetChannelSubscriptions(String channel, int limit, int offset, boolean ascending, String oauth) throws JSONException {
        return GetChannelSubscriptions(channel, limit, offset, ascending);
    }

    /**
     * Gets a stream object
     *
     * @param channel
     * @return
     */
    public JSONObject GetStream(String channel) throws JSONException {
        List<String> user_id = new ArrayList<>();
        user_id.add(this.getIDFromChannel(channel));
        return this.translateGetStream(Helix.instance().getStreams(1, null, null, user_id, null, null, null));
    }

    private JSONObject translateGetStream(JSONObject streamData) {
        return streamData;
    }

    /**
     * Gets a streams object array. Each channel id should be seperated with a comma.
     *
     * @param channels
     * @return
     */
    public JSONObject GetStreams(String channels) throws JSONException {
        List<String> user_id = new ArrayList<>();
        user_id.addAll(Arrays.asList(channels.split(",")));
        return this.translateGetStreams(Helix.instance().getStreams(user_id.size(), null, null, user_id, null, null, null));
    }

    private JSONObject translateGetStreams(JSONObject streamData) {
        return streamData;
    }

    /**
     * Gets the communities object array.
     *
     * @param channel
     * @return
     */
    public JSONObject GetCommunities(String channel) throws JSONException {
        throw new UnsupportedOperationException("removed by Twitch");
    }

    /**
     * Gets a user object by user name
     *
     * @param user
     * @return
     */
    public JSONObject GetUser(String user) throws JSONException {
        List<String> user_login = new ArrayList<>();
        user_login.add(user);
        return this.translateGetUser(Helix.instance().getUsers(null, user_login));
    }

    private JSONObject translateGetUser(JSONObject userData) {
        JSONObject result = new JSONObject();
        userData = userData.getJSONArray("data").getJSONObject(0);

        result.put("_id", Long.parseLong(userData.getString("id")));
        result.put("bio", userData.getString("description"));
        result.put("created_at", userData.getString("created_at"));
        result.put("display_name", userData.getString("display_name"));
        result.put("logo", userData.getString("profile_image_url"));
        result.put("name", userData.getString("login"));
        result.put("type", userData.getString("type"));
        result.put("updated_at", "");

        return result;
    }

    /**
     * Gets a user object by ID
     *
     * @param user
     * @return
     */
    public JSONObject GetUserByID(String userID) throws JSONException {
        List<String> user_id = new ArrayList<>();
        user_id.add(userID);
        return this.translateGetUser(Helix.instance().getUsers(user_id, null));
    }

    /**
     * Runs a commercial. Will fail if channel is not partnered, a commercial has been run in the last 8 minutes, or stream is offline
     *
     * @param channel
     * @param length (30, 60, 90, 120, 150, 180)
     * @return jsonObj.getInt("_http") == 422 if length is invalid or the channel is currently ineligible to run a commercial due to restrictions
     * listed in the method description
     */
    public JSONObject RunCommercial(String channel, int length) throws JSONException {
        return this.translateRunCommercial(Helix.instance().startCommercial(this.getIDFromChannel(channel), length));
    }

    private JSONObject translateRunCommercial(JSONObject commercialData) {
        return commercialData;
    }

    /**
     * Gets a list of users in the channel
     *
     * @param channel
     * @return
     */
    public JSONObject GetChatUsers(String channel) throws JSONException {
        return new JSONObject(HttpRequest.getData(HttpRequest.RequestType.GET, "https://tmi.twitch.tv/group/user/" + channel + "/chatters", null, null).content);
    }

    /**
     * Checks if a user is following a channel
     *
     * @param user
     * @param channel
     * @return
     */
    public JSONObject GetUserFollowsChannel(String user, String channel) throws JSONException {
        return this.translateGetUserFollowsChannel(Helix.instance().getUsersFollows(this.getIDFromChannel(user), this.getIDFromChannel(channel), 1, null));
    }

    private JSONObject translateGetUserFollowsChannel(JSONObject followData) {
        return followData;
    }

    /**
     * Gets the full list of emotes from Twitch
     *
     * @return
     */
    public JSONObject GetEmotes() throws JSONException {
        JSONArray arr = Helix.instance().getGlobalEmotes().getJSONArray("data");
        JSONObject jo2 = Helix.instance().getChannelEmotes(this.getIDFromChannel(PhantomBot.instance().getChannelName()));

        if (jo2.getInt("_http") == 200) {
            jo2.getJSONArray("data").forEach(obj -> {
                arr.put(obj);
            });
        }

        return this.translateGetEmotes(arr);
    }

    private JSONObject translateGetEmotes(JSONArray arr) {
        JSONObject jso = new JSONObject();
        jso.put("data", arr);
        return jso;
    }

    /**
     * Gets the list of cheer emotes from Twitch
     *
     * @return
     */
    public JSONObject GetCheerEmotes() throws JSONException {
        return this.translateGetCheerEmotes(Helix.instance().getCheermotes(null));
    }

    private JSONObject translateGetCheerEmotes(JSONObject cheerData) {
        return cheerData;
    }

    private String cheerEmotes = "";

    /**
     * Builds a RegExp String to match cheer emotes from Twitch
     *
     * @return
     */
    public String GetCheerEmotesRegex() throws JSONException {
        String[] emoteList;
        JSONObject jsonInput;
        JSONArray jsonArray;

        if (cheerEmotes.isBlank()) {
            jsonInput = GetCheerEmotes();
            if (jsonInput.has("actions")) {
                jsonArray = jsonInput.getJSONArray("actions");
                emoteList = new String[jsonArray.length()];
                for (int idx = 0; idx < jsonArray.length(); idx++) {
                    emoteList[idx] = "\\b" + jsonArray.getJSONObject(idx).getString("prefix") + "\\d+\\b";
                }
                cheerEmotes = String.join("|", emoteList);
            }
        }
        return cheerEmotes;
    }

    /**
     * Gets the list of VODs from Twitch
     *
     * @param channel The channel requesting data for
     * @param type The type of data: current, highlights, archives
     * @return String List of Twitch VOD URLs (as a JSON String) or empty String in failure.
     */
    public String GetChannelVODs(String channel, String type) throws JSONException {
        JSONStringer jsonOutput = new JSONStringer();
        JSONObject jsonInput;
        JSONArray jsonArray;

        if (type.equals("current")) {
            jsonInput = this.translateGetChannelVODs(Helix.instance().getVideos(null, this.getIDFromChannel(channel), null, 1, null, null, null, null, "time", "archive"));
            if (jsonInput.has("videos")) {
                jsonArray = jsonInput.getJSONArray("videos");
                if (jsonArray.length() > 0) {
                    if (jsonArray.getJSONObject(0).has("status")) {
                        if (jsonArray.getJSONObject(0).getString("status").equals("recording")) {
                            jsonOutput.object().key("videos").array().object();
                            jsonOutput.key("url").value(jsonArray.getJSONObject(0).getString("url"));
                            jsonOutput.key("recorded_at").value(jsonArray.getJSONObject(0).getString("recorded_at"));
                            jsonOutput.key("length").value(jsonArray.getJSONObject(0).getInt("length"));
                            jsonOutput.endObject().endArray().endObject();
                        }
                        com.gmt2001.Console.debug.println("TwitchAPIv5::GetChannelVODs: " + jsonOutput.toString());
                        if (jsonOutput.toString() == null) {
                            return "";
                        }
                        return jsonOutput.toString();
                    }
                }
            }
        }

        if (type.equals("highlights")) {
            jsonInput = this.translateGetChannelVODs(Helix.instance().getVideos(null, this.getIDFromChannel(channel), null, 5, null, null, null, null, "time", "highlight"));
            if (jsonInput.has("videos")) {
                jsonArray = jsonInput.getJSONArray("videos");
                if (jsonArray.length() > 0) {
                    jsonOutput.object().key("videos").array();
                    for (int idx = 0; idx < jsonArray.length(); idx++) {
                        jsonOutput.object();
                        jsonOutput.key("url").value(jsonArray.getJSONObject(idx).getString("url"));
                        jsonOutput.key("recorded_at").value(jsonArray.getJSONObject(idx).getString("recorded_at"));
                        jsonOutput.key("length").value(jsonArray.getJSONObject(idx).getInt("length"));
                        jsonOutput.endObject();
                    }
                    jsonOutput.endArray().endObject();
                    com.gmt2001.Console.debug.println("TwitchAPIv5::GetChannelVODs: " + jsonOutput.toString());
                    if (jsonOutput.toString() == null) {
                        return "";
                    }
                    return jsonOutput.toString();
                }
            }
        }

        if (type.equals("archives")) {
            jsonInput = this.translateGetChannelVODs(Helix.instance().getVideos(null, this.getIDFromChannel(channel), null, 5, null, null, null, null, "time", "archive"));
            if (jsonInput.has("videos")) {
                jsonArray = jsonInput.getJSONArray("videos");
                if (jsonArray.length() > 0) {
                    jsonOutput.object().key("videos").array();
                    for (int idx = 0; idx < jsonArray.length(); idx++) {
                        jsonOutput.object();
                        jsonOutput.key("url").value(jsonArray.getJSONObject(idx).getString("url"));
                        jsonOutput.key("recorded_at").value(jsonArray.getJSONObject(idx).getString("recorded_at"));
                        jsonOutput.key("length").value(jsonArray.getJSONObject(idx).getInt("length"));
                        jsonOutput.endObject();
                    }
                    jsonOutput.endArray().endObject();
                    com.gmt2001.Console.debug.println("TwitchAPIv5::GetChannelVODs: " + jsonOutput.toString());
                    if (jsonOutput.toString() == null) {
                        return "";
                    }
                    return jsonOutput.toString();
                }
            }
        }

        /* Just return an empty string. */
        return "";
    }

    private JSONObject translateGetChannelVODs(JSONObject vodData) {
        return vodData;
    }

    /**
     * Returns when a Twitch account was created.
     *
     * @param channel
     * @return String date-time representation (2015-05-09T00:08:04Z)
     */
    public String getChannelCreatedDate(String channel) throws JSONException {
        JSONObject jsonInput = this.GetUser(channel);
        if (jsonInput.has("created_at")) {
            return jsonInput.getString("created_at");
        }
        return "ERROR";
    }

    /**
     * Method that gets the teams that the channel is in.
     *
     * @param channelName
     * @return
     */
    public JSONObject getChannelTeams(String channelName) throws JSONException {
        return this.translateGetChannelTeams(Helix.instance().getChannelTeams(this.getIDFromChannel(channelName)));
    }

    private JSONObject translateGetChannelTeams(JSONObject teamsData) {
        return teamsData;
    }

    /**
     * Method that gets a Twitch team.
     *
     * @param teamName
     * @return
     */
    public JSONObject getTeam(String teamName) throws JSONException {
        return this.translateGetTeam(Helix.instance().getTeams(teamName, null));
    }

    private JSONObject translateGetTeam(JSONObject teamData) {
        return teamData;
    }

    /**
     * Checks to see if the bot account is verified by Twitch.
     *
     * @param channel
     * @return boolean true if verified
     */
    public boolean getBotVerified(String channel) throws JSONException {
        throw new UnsupportedOperationException("removed by Twitch");
    }

    /**
     * Get the clips from today for a channel.
     *
     * @param channel
     * @return JSONObject clips object.
     */
    public JSONObject getClipsToday(String channel) throws JSONException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        String start = sdf.format(c.getTime());
        c.add(Calendar.DAY_OF_MONTH, 1);
        String end = sdf.format(c.getTime());
        return this.translateGetClipsToday(Helix.instance().getClips(null, this.getIDFromChannel(channel), null, 100, null, null, start, end));
    }

    private JSONObject translateGetClipsToday(JSONObject clipsData) {
        return clipsData;
    }

    /**
     * Populates the followed table from a JSONArray. The database auto commit is disabled as otherwise the large number of writes in a row can cause
     * some delay. We only update the followed table if the user has an entry in the time table. This way we do not potentially enter thousands, or
     * tens of thousands, or even more, entries into the followed table for folks that do not visit the stream.
     *
     * @param JSONArray JSON array object containing the followers data
     * @param DataStore Copy of database object for writing
     * @return int How many objects were inserted into the database
     */
    private int PopulateFollowedTable(JSONArray followsArray, DataStore dataStore) throws JSONException {
        int insertCtr = 0;
        for (int idx = 0; idx < followsArray.length(); idx++) {
            if (dataStore.exists("time", followsArray.getJSONObject(idx).getString("from_name"))) {
                insertCtr++;
                dataStore.set("followed_fixtable", followsArray.getJSONObject(idx).getString("from_name"), "true");
            }
        }
        return insertCtr;
    }

    /**
     * Updates the followed table with a complete list of followers. This should only ever be executed once, when the database does not have complete
     * list of followers.
     *
     * @param String ID of the channel to lookup data for
     * @param DataStore Copy of database object for reading data from
     * @param int Total number of followers reported from Twitch API
     */
    @SuppressWarnings("SleepWhileInLoop")
    private void FixFollowedTableWorker(String channelId, DataStore dataStore, int followerCount) throws JSONException {
        int insertCtr = 0;
        JSONObject jsonInput;
        String after = null;

        com.gmt2001.Console.out.println("FixFollowedTable: Retrieving followers that exist in the time table, this may take some time.");

        /* Perform the lookups. The initial lookup will return the next API endpoint
         * as a _cursor object. Use this to build the next query. We do this to prepare
         * for Twitch API v5 which will require this.
         */
        do {
            jsonInput = Helix.instance().getUsersFollows(null, channelId, 100, after);
            if (!jsonInput.has("data")) {
                return;
            }

            insertCtr += PopulateFollowedTable(jsonInput.getJSONArray("data"), dataStore);

            if (!jsonInput.has("pagination") || !jsonInput.getJSONObject("pagination").has("cursor") || jsonInput.getJSONObject("pagination").getString("cursor").isBlank()) {
                break;
            }

            after = jsonInput.getJSONObject("pagination").getString("cursor");

            /* Be kind to Twitch during this process. */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                /* Since it might be possible that we have hundreds, even thousands of calls,
                 * we do not dump even a debug statement here.
                 */
            }
        } while (!jsonInput.getJSONArray("data").isEmpty());

        dataStore.RenameFile("followed_fixtable", "followed");
        com.gmt2001.Console.out.println("FixFollowedTable: Pulled followers into the followed table, loaded " + insertCtr + "/" + followerCount + " records.");
    }

    /**
     * Wrapper to perform the followed table updated. In order to ensure that PhantomBot does not get stuck trying to perform this work, a thread is
     * spawned to perform the work.
     *
     * @param channel Name of the channel to lookup data for
     * @param dataStore Copy of database object
     * @param force Force the run even if the number of followers is too high
     */
    public void FixFollowedTable(String channel, DataStore dataStore, Boolean force) throws JSONException {

        /* Determine number of followers to determine if this should not execute unless forced. */
        JSONObject jsonInput = Helix.instance().getUsersFollows(null, this.getIDFromChannel(channel), 1, null);
        if (!jsonInput.has("total")) {
            com.gmt2001.Console.err.println("Failed to pull follower count for FixFollowedTable");
            return;
        }
        int followerCount = jsonInput.getInt("total");
        if (followerCount > 10000 && !force) {
            com.gmt2001.Console.out.println("Follower count is above 10,000 (" + followerCount + "). Not executing. You may force this.");
            return;
        }

        try {
            FixFollowedTableRunnable fixFollowedTableRunnable = new FixFollowedTableRunnable(this.getIDFromChannel(channel), dataStore, followerCount);
            new Thread(fixFollowedTableRunnable, "com.gmt2001.TwitchAPIv5::fixFollowedTable").start();
        } catch (Exception ex) {
            com.gmt2001.Console.err.println("Failed to start thread for updating followed table.");
        }
    }

    /**
     * Tests the Twitch API to ensure that authentication is good.
     *
     * @return
     */
    public boolean TestAPI() throws JSONException {
        return true;
    }

    /**
     * Returns a username when given an Oauth.
     *
     * @param userOauth Oauth to check with.
     * @return String The name of the user or null to indicate that there was an error.
     */
    public String GetUserFromOauth(String userOauth) throws JSONException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the channel Id
     *
     * @param channel channel name
     * @return int the channel id.
     */
    public int getChannelId(String channel) {
        return Integer.parseUnsignedInt(this.getIDFromChannel(channel));
    }

    /**
     * Class for Thread for running the FixFollowedTableWorker job in the background.
     */
    private class FixFollowedTableRunnable implements Runnable {

        private final DataStore dataStore;
        private final String channel;
        private final int followerCount;

        public FixFollowedTableRunnable(String channel, DataStore dataStore, int followerCount) {
            this.channel = channel;
            this.dataStore = dataStore;
            this.followerCount = followerCount;
        }

        @Override
        public void run() {
            try {
                FixFollowedTableWorker(channel, dataStore, followerCount);
            } catch (JSONException ex) {
                com.gmt2001.Console.err.logStackTrace(ex);
            }
        }
    }
}
