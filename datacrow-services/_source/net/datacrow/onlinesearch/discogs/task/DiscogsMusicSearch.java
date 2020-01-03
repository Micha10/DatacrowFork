/******************************************************************************
 *                                     __                                     *
 *                              <-----/@@\----->                              *
 *                             <-< <  \\//  > >->                             *
 *                               <-<-\ __ /->->                               *
 *                               Data /  \ Crow                               *
 *                                   ^    ^                                   *
 *                              info@datacrow.net                             *
 *                                                                            *
 *                       This file is part of Data Crow.                      *
 *       Data Crow is free software; you can redistribute it and/or           *
 *        modify it under the terms of the GNU General Public                 *
 *       License as published by the Free Software Foundation; either         *
 *              version 3 of the License, or any later version.               *
 *                                                                            *
 *        Data Crow is distributed in the hope that it will be useful,        *
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.             *
 *           See the GNU General Public License for more details.             *
 *                                                                            *
 *        You should have received a copy of the GNU General Public           *
 *  License along with this program. If not, see http://www.gnu.org/licenses  *
 *                                                                            *
 ******************************************************************************/

package net.datacrow.onlinesearch.discogs.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnection;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.MusicAlbum;
import net.datacrow.core.objects.helpers.MusicTrack;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.Region;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.CoreUtilities;
import net.datacrow.core.utilities.StringUtils;
import net.datacrow.util.HttpOAuthHelper;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

//import sun.misc.IOUtils;

/**
 * @author Robert Jan van der Waals
 */
public class DiscogsMusicSearch extends SearchTask {

    private static Logger logger = Logger.getLogger(DiscogsMusicSearch.class.getName());

    private static final String _CONSUMER_KEY = "RNyvjXZbeERWnGURrLdG";
    private static final String _CONSUMER_SECRET = "sLGNmziJAJIvnXKUJkKlzhZdyZixnNbC";
    
    private static final int maxQuerySize = 5;
    
    public DiscogsMusicSearch(  IOnlineSearchClient listener, 
                                IServer server, 
                                Region region, 
                                SearchMode mode,
                                String query) {
        
        super(listener, server, region, mode, query);
    }

    @Override
    public String getWhiteSpaceSubst() {
        return " ";
    }
    
    private HttpClient getHttpClient() {
        HttpClient httpClient = new DefaultHttpClient();
        httpClient.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "DataCrow/4.0 +http://www.datacrow.net");
        return httpClient;
    }
    
    @Override
    protected Collection<Object> getItemKeys() throws Exception {
        HttpClient httpClient = getHttpClient();
        
        List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
        queryParams.add(new BasicNameValuePair("q", getQuery()));
        
        URIBuilder builder = new URIBuilder();
        builder.setScheme("http");
        builder.setHost("api.discogs.com");
        builder.setPath("/database/search");
        builder.setQuery(URLEncodedUtils.format(queryParams, "UTF-8"));
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        
        HttpOAuthHelper au = new HttpOAuthHelper("application/json");
        au.handleRequest(httpGet, _CONSUMER_KEY, _CONSUMER_SECRET, null, queryParams);

        HttpResponse httpResponse = httpClient.execute(httpGet);
        String response = getReponseText(httpResponse);
        
        httpClient.getConnectionManager().shutdown();
        
        Collection<Object> keys = new ArrayList<Object>();
        
        int counter = 0;
        for (String key : StringUtils.getValuesBetween("\"id\":", "}", response)) {
            keys.add(key.trim());
            
            if (counter++ >= maxQuerySize) break;
        }
        return keys;
    }

    @Override
    protected DcObject getItem(Object key, boolean full) throws Exception {
        HttpClient httpClient = getHttpClient();
        
        URIBuilder builder = new URIBuilder();
        builder.setScheme("http");
        builder.setHost("api.discogs.com");
        builder.setPath("/release/" + key);
        URI uri = builder.build();
        HttpGet httpGet = new HttpGet(uri);
        
        HttpOAuthHelper au = new HttpOAuthHelper("application/json");
        au.handleRequest(httpGet, _CONSUMER_KEY, _CONSUMER_SECRET);

        HttpResponse httpResponse = httpClient.execute(httpGet);
        String response = getReponseText(httpResponse);
        httpClient.getConnectionManager().shutdown();
        
        MusicAlbum ma = new MusicAlbum();

        ma.addExternalReference(DcRepository.ExternalReferences._DISCOGS, (String) key);
        ma.setValue(DcObject._SYS_SERVICEURL, uri.toString());
        
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = (JsonObject) jsonParser.parse(response);
        JsonObject eRespone = jsonObject.getAsJsonObject("resp");
        JsonObject eRelease = eRespone.getAsJsonObject("release");
        
        if (eRelease != null) {
            ma.setValue(MusicAlbum._A_TITLE, eRelease.get("title").getAsString());
            ma.setValue(MusicAlbum._C_YEAR, eRelease.get("year").getAsString());
            ma.setValue(MusicAlbum._N_WEBPAGE, eRelease.get("uri").getAsString());
            
            ma.createReference(MusicAlbum._F_COUNTRY, eRelease.get("country").getAsString());
            
            setStorageMedium(ma, eRelease);
            setRating(ma, eRelease);
            setGenres(ma, eRelease);
            setArtists(ma, eRelease);
            addTracks(ma, eRelease);
            setImages(ma, eRelease);
        }
        
        Thread.sleep(1000);
        return ma;
    }
    
    private void addTracks(MusicAlbum ma, JsonObject eRelease) {
        JsonArray arTracks = eRelease.get("tracklist").getAsJsonArray();
        JsonObject oTrack;
        MusicTrack mt;
        
        for (JsonElement eTrack : arTracks) {
            oTrack = eTrack.getAsJsonObject();
            
            // not a track
            if (!oTrack.get("type_").getAsString().equalsIgnoreCase("track")) continue;
            
            mt = new MusicTrack();

            mt.setValue(MusicTrack._A_TITLE, oTrack.get("title").getAsString());
            
            setArtists(mt, eRelease, oTrack);
            setTrackNumber(mt, oTrack);
            setPlayLength(mt, oTrack);
            
            ma.addChild(mt);
        }
    }
    
    private void setImages(MusicAlbum ma, JsonObject eRelease) {
        
        if (eRelease.get("images") == null) return;
        
        JsonArray arImages = eRelease.get("images").getAsJsonArray();
        JsonObject oImage;
        String uri; 
        
        for (JsonElement eImage : arImages) {
            oImage = eImage.getAsJsonObject();
            
            uri = oImage.get("uri").getAsString();
            String imageName = new File(uri).getName();
            
            try {
                HttpConnection connection = new HttpConnection(new URL("http://s.pixogs.com/image/" + imageName));
                byte[] imageData = connection.getBytes();
                connection.close();
                
                ma.setValue(MusicAlbum._J_PICTUREFRONT, new DcImageIcon(imageData));
                break;
            } catch (Exception e) {
                logger.error("An error occurred while retrieving image from " + uri, e);
                listener.addError("An error occurred while retrieving image from " + uri);
            }
        }
    }
    
    private void setStorageMedium(MusicAlbum ma, JsonObject eRelease) {
        JsonArray arFormats = eRelease.get("formats").getAsJsonArray();
        for (JsonElement eFormat : arFormats) {
            JsonObject oFormat = (JsonObject) eFormat;
            ma.createReference(MusicAlbum._I_STORAGEMEDIUM, oFormat.get("name").getAsString());
        }
    }
    
    private void setRating(MusicAlbum ma, JsonObject eRelease) {
        JsonObject eCommunity = eRelease.get("community").getAsJsonObject();
        if (eCommunity != null) {
            JsonObject eRating = eCommunity.get("rating").getAsJsonObject();
            Double rating = eRating.get("average").getAsDouble();
            ma.setValue(MusicAlbum._E_RATING, Math.round(rating * 2));
        }
    }
    
    private void setGenres(MusicAlbum ma, JsonObject eRelease) {
        JsonArray arGenres = eRelease.get("genres").getAsJsonArray();
        for (JsonElement eGenre : arGenres) {
            String genre = eGenre.getAsString();
            ma.createReference(MusicAlbum._G_GENRES, genre);
        }
    }
    
    private void setArtists(MusicAlbum ma, JsonObject eRelease) {
        String artist;
        JsonArray arArtists = eRelease.get("artists").getAsJsonArray();
        for (JsonElement eArtist : arArtists) {
            artist = eArtist.getAsJsonObject().get("name").getAsString();
            ma.createReference(MusicAlbum._F_ARTISTS, artist);
        }
    }
    
    private void setArtists(MusicTrack mt, JsonObject eRelease, JsonObject oTrack) {
        JsonArray arArtists = oTrack.get("artists") != null ? 
                oTrack.get("artists").getAsJsonArray() : 
                eRelease.get("artists").getAsJsonArray();
    
        String artist;
        for (JsonElement eArtist : arArtists) {
            artist = eArtist.getAsJsonObject().get("name").getAsString();
            mt.createReference(MusicTrack._G_ARTIST, artist);
        }
    }
    
    private void setTrackNumber(MusicTrack mt, JsonObject oTrack) {
        String track = oTrack.get("position").getAsString();
        if (track.trim().length() > 0) {
            mt.setValue(MusicTrack._F_TRACKNUMBER, track);
        }
    }
    
    private void setPlayLength(MusicTrack mt, JsonObject oTrack) {
        String duration =  oTrack.get("duration").getAsString();
                
        if (!CoreUtilities.isEmpty(duration) && duration.contains(":")) {
            int minutes = 0;
            int hours = 0;
            
            try {
                int seconds = Integer.parseInt(duration.substring(duration.lastIndexOf(":") + 1));
                duration = duration.substring(0, duration.lastIndexOf(":"));
                
                if (duration.indexOf(":") > 0) {
                    minutes = Integer.parseInt(duration.substring(duration.lastIndexOf(":") + 1));
                    duration = duration.substring(0, duration.lastIndexOf(":"));
                    hours = Integer.parseInt(duration);
                } else {
                    minutes = Integer.parseInt(duration);
                }
                
                mt.setValue(MusicTrack._J_PLAYLENGTH, new Long(seconds + (minutes * 60) + (hours * 3600)));
                
            } catch (NumberFormatException nfe) {
                logger.error("Error while parsing the duration for " + mt, nfe);
            }
        }
    }
    
    @Override
    protected DcObject getItem(URL url) throws Exception {
        return null;
    }
    
    private String getReponseText(HttpResponse response) throws IOException {
        InputStream is = response.getEntity().getContent();
        String text = null;
        try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            text = scanner.useDelimiter("\\A").next();
        }
        //String s = new String(IOUtils.readFully(is, -1, true));
        is.close();
        return text;
    }
}
