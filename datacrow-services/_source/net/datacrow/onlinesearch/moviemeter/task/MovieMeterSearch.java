package net.datacrow.onlinesearch.moviemeter.task;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.modules.DcModules;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Movie;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.CoreUtilities;
import net.datacrow.core.utilities.StringUtils;
import net.datacrow.core.utilities.html.HtmlUtils;
import net.datacrow.core.utilities.json.JSONArray;
import net.datacrow.core.utilities.json.JSONObject;

import org.apache.log4j.Logger;

public class MovieMeterSearch extends SearchTask {
    
    private static final Logger logger = Logger.getLogger(MovieMeterSearch.class.getName());
    private static final String apiKey = "xs3cw1v9et5vwjt3wstu7xfh0zjy8g1u";
    
    public MovieMeterSearch(IOnlineSearchClient listener, IServer server, String query) {
        super(listener, server, null, null, query);
    }
    
    @Override
    public String getWhiteSpaceSubst() {
        return "%20";
    }
    
    private void setValue(DcObject movie, int fieldIdx, Object value) {
        if (!CoreUtilities.isEmpty(value))
            movie.setValue(fieldIdx, value);
    }
    
    @Override
    @SuppressWarnings("rawtypes")
    protected DcObject getItem(Object key, boolean full) throws Exception {
        DcObject movie = DcModules.get(DcModules._MOVIE).getItem();

        try {
            String url = getAddress() + key + "?api_key=" + apiKey;
            String result = HtmlUtils.getHtmlCleaned(new URL(url), "UTF-8", 0);
            
            result = result.endsWith("]") ? result.substring(0, result.length() - 1) : result;
            result = result.startsWith("[") ? result.substring(1) : result;
            
            JSONObject jo = new JSONObject(result);
            
            movie.setValue(DcObject._SYS_SERVICEURL, getAddress() + key);
            movie.addExternalReference(DcRepository.ExternalReferences._MOVIEMETER, String.valueOf(key));
            
            setValue(movie, Movie._G_WEBPAGE, jo.get("url"));
            setValue(movie, Movie._A_TITLE, jo.get("title"));
            setValue(movie, Movie._C_YEAR, jo.get("year"));
            setValue(movie, Movie._B_DESCRIPTION, jo.get("plot"));
            
            String imdb = jo.getString("imdb");
            if (!CoreUtilities.isEmpty(imdb))
                movie.addExternalReference(DcRepository.ExternalReferences._IMDB, "tt" + imdb);
            
            if (!CoreUtilities.isEmpty(jo.getDouble("average"))) {
                double rating = jo.getDouble("average");
                rating = rating * 2;
                movie.setValue(Movie._E_RATING, Math.round(rating));
            }
            
            JSONObject posters = jo.getJSONObject("posters");
            String picURL = null;
            if (posters.has("large"))
                picURL = posters.getString("large");
            else if (posters.has("regular"))
                picURL = posters.getString("regular");
            else if (posters.has("small"))
                picURL = posters.getString("small");
            else if (posters.has("thumb"))
                picURL = posters.getString("thumb");
            
            if (picURL != null)
                movie.setValue(Movie._X_PICTUREFRONT, new DcImageIcon(HttpConnectionUtil.retrieveBytes(picURL)));
             
            Long duration = jo.getLong("duration");
            if (!CoreUtilities.isEmpty(duration)) {
                movie.setValue(Movie._L_PLAYLENGTH, Long.valueOf(duration.longValue() * 60));
            }
    
            setValue(movie, Movie._F_TITLE_LOCAL, jo.getString("alternative_title"));
               
            JSONArray countries = jo.getJSONArray("countries");
            String country;
            for (int i = 0; i < countries.length(); i++) {
                country = countries.getString(i);
                movie.createReference(Movie._F_COUNTRY, country);
            }
                
            JSONArray genres = jo.getJSONArray("genres");
            String genre;
            for (int i = 0; i < genres.length(); i++) {
                genre = genres.getString(i);
                movie.createReference(Movie._H_GENRES, genre);
            }
            
            JSONArray actors = jo.getJSONArray("actors");
            JSONObject actor;
            for (int i = 0; i < actors.length(); i++) {
                actor = actors.getJSONObject(i);
                movie.createReference(Movie._I_ACTORS, actor.get("name"));
            }
    
            JSONArray directors = jo.getJSONArray("directors");
            String director;
            for (int i = 0; i < directors.length(); i++) {
                director = directors.getString(i);
                movie.createReference(Movie._J_DIRECTOR, director);
            }
        } catch (Exception e) {
            logger.error(e, e);
        }
        return movie;
    }

    @Override
    protected DcObject getItem(URL url) throws Exception {
        String key = url.toString();
        key = key.substring(key.lastIndexOf("/") + 1);
        return getItem(Integer.valueOf(key), true);
    }

    @Override
    protected Collection<Object> getItemKeys() throws Exception {
        Collection<Object> ids = new ArrayList<Object>();
        String url = getAddress() + "?api_key=" + apiKey + "&q=" + getQuery();
        
        String result = HtmlUtils.getHtmlCleaned(new URL(url), "UTF-8", 0);
        int idx = result.indexOf("\"id\":");
        String id = "";
        while (idx > -1) {
            id = StringUtils.getValueBetween("\"id\":", ",", result);
            result = result.substring(result.indexOf("\"id\":" + id + ",") + ("\"id\":" + id + ",").length());
            ids.add(id);
            idx = result.indexOf("\"id\":");
        }
        
        return ids;
    }
}
