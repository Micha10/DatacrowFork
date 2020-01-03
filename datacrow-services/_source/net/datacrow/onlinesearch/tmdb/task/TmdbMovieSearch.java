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

package net.datacrow.onlinesearch.tmdb.task;

import com.omertron.themoviedbapi.MovieDbException;
import com.omertron.themoviedbapi.TheMovieDbApi;
import com.omertron.themoviedbapi.enumeration.ArtworkType;
import com.omertron.themoviedbapi.enumeration.SearchType;
import com.omertron.themoviedbapi.model.Genre;
import com.omertron.themoviedbapi.model.artwork.Artwork;
import com.omertron.themoviedbapi.model.credits.MediaCreditCast;
import com.omertron.themoviedbapi.model.credits.MediaCreditCrew;
import com.omertron.themoviedbapi.model.movie.MovieInfo;
import com.omertron.themoviedbapi.results.ResultList;
import net.datacrow.core.DcRepository;
import net.datacrow.core.DcRepository.ExternalReferences;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.modules.DcModules;
import net.datacrow.core.objects.DcAssociate;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Movie;
import net.datacrow.core.services.*;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.CoreUtilities;
import org.apache.log4j.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

//import com.omertron.themoviedbapi.model.artwork.ArtworkType;
//import com.omertron.themoviedbapi.model.MovieDb;
//import com.omertron.themoviedbapi.model.PersonCast;
//import com.omertron.themoviedbapi.model.PersonCrew;
//import com.omertron.themoviedbapi.results.TmdbResultsList;

public class TmdbMovieSearch extends SearchTask {

    private static Logger logger = Logger.getLogger(TmdbMovieSearch.class.getName());
    private static TheMovieDbApi tmdb;
    
    private static String API_KEY = "20cdab5da434fda12000fc1bbcbf2afe";
    
    public TmdbMovieSearch(IOnlineSearchClient listener, IServer server, Region region, SearchMode mode, String query) {
        super(listener, server, region, mode, query);
        try {
            tmdb = new TheMovieDbApi(API_KEY);
        } catch (Exception e) {
            logger.error(e, e);
        }
    }

    @Override
    public String getWhiteSpaceSubst() {
        return " ";
    }
    
    @Override
    public DcObject query(DcObject dco) throws Exception {
        return getItem(dco, true);
    }
    
    @Override
    protected DcObject getItem(URL url) throws Exception {
        return null;
    }

    @Override
    protected DcObject getItem(Object key, boolean full) throws MovieDbException {
        Movie movie = (Movie) key;
        
        String movieId = movie.getExternalReference(ExternalReferences._TMDB);
        MovieInfo mdb = tmdb.getMovieInfo(Integer.parseInt(movieId), getRegion().getCode(), "images,casts,list,crew");
        
        if (full) {
            setImages(mdb, movie);
            setGenres(mdb, movie);
            setCast(mdb, movie);
            setCrew(mdb, movie);
        }
        
        return movie;
    }
    
    private void setImages(MovieInfo mdb, Movie movie) {
        try {
            byte[] img;
            String imgUrl;
            for (Artwork aw : mdb.getImages()) {
                imgUrl = aw.getFilePath();
                
                if (CoreUtilities.isEmpty(imgUrl)) continue;
                
                try {
                    if (aw.getArtworkType() == ArtworkType.POSTER) {
                        img = HttpConnectionUtil.retrieveBytes(tmdb.createImageUrl(imgUrl, "original"));
                        movie.setValue(Movie._X_PICTUREFRONT, new DcImageIcon(img));
                    } else if (aw.getArtworkType() == ArtworkType.BACKDROP) {
                        img = HttpConnectionUtil.retrieveBytes(imgUrl);
                        movie.setValue(Movie._Y_PICTUREBACK, new DcImageIcon(img));
                    }
                } catch (Exception e) {
                    logger.debug("Failed to retrieve image from " + imgUrl, e);
                }
            }
        } catch (Exception e) {
            logger.error(e, e);
        }
    }
    
    private void setCast(MovieInfo mdb, Movie movie) {
        try {
            DcObject dco;
            byte[] img;
            URL imgUrl;
            for (MediaCreditCast pc : mdb.getCast()) {
                dco = movie.createReference(Movie._I_ACTORS, pc.getName());
                if (dco.isNew()) {
                    if (DcModules.get(DcModules._MOVIE).getSettings().getBoolean(DcRepository.ModuleSettings.stOnlineSearchSubItems))  {
                        if (CoreUtilities.isEmpty(pc.getArtworkPath())) continue;
                        
                        imgUrl = tmdb.createImageUrl(pc.getArtworkPath(), "original");
                        img = HttpConnectionUtil.retrieveBytes(imgUrl);
                        dco.setValue(DcAssociate._D_PHOTO, new DcImageIcon(img));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve actors for " + movie, e);
        }
    }
    
    private void setCrew(MovieInfo mdb, Movie movie) {
        try {
            DcObject dco;
            byte[] img;
            URL imgUrl;
            for (MediaCreditCrew pc : mdb.getCrew()) {
                if (pc.getJob() == null) continue;
                
                if (pc.getJob().equalsIgnoreCase("director")) {
                    dco = movie.createReference(Movie._J_DIRECTOR, pc.getName());
                    if (dco.isNew()) {
                        if (DcModules.get(DcModules._MOVIE).getSettings().getBoolean(DcRepository.ModuleSettings.stOnlineSearchSubItems))  {
                            if (CoreUtilities.isEmpty(pc.getArtworkPath())) continue;

                            imgUrl = tmdb.createImageUrl(pc.getArtworkPath(), "original");
                            img = HttpConnectionUtil.retrieveBytes(imgUrl);
                            dco.setValue(DcAssociate._D_PHOTO, new DcImageIcon(img));
                        }
                    }   
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve director for " + movie, e);
        }
    }
    
    private void setGenres(MovieInfo mdb, Movie movie) {
        try {
            for (Genre genre : mdb.getGenres()) {
            	movie.createReference(Movie._H_GENRES, genre.getName());
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve genres for " + movie, e);
        }
    }

    @Override
    protected void preSearchCheck() {
        SearchTaskUtilities.checkForIsbn(this);
    }
    
    @Override
    protected Collection<Object> getItemKeys() throws Exception {
        Collection<Object> keys = new ArrayList<Object>();

        ResultList<MovieInfo> movieList = tmdb.searchMovie(getQuery(), 0, "", true, 0, 0, SearchType.PHRASE);
        
        String date;
        Movie movie;

//        float rating;
        for (MovieInfo mdb : movieList.getResults()) {
            mdb = tmdb.getMovieInfo(mdb.getId(), getRegion().getCode());

            movie = new Movie();
            movie.setValue(Movie._A_TITLE, mdb.getTitle());
            movie.setValue(Movie._G_WEBPAGE, mdb.getHomepage());
            movie.setValue(Movie._F_TITLE_LOCAL, mdb.getOriginalTitle());
            movie.setValue(Movie._B_DESCRIPTION, mdb.getOverview());
            
            
            setServiceInfo(movie);
//            rating = mdb.getReviews()();
//            if (rating > 0) {
//                try {
//                    movie.setValue(Movie._E_RATING, Math.floor(rating));
//                } catch (Exception e) {
//                    logger.debug("Error while converting rating " + rating, e);
//                }
//            }
            
            date = mdb.getReleaseDate();
            if (!CoreUtilities.isEmpty(date) && date.length() > 4) {
                movie.setValue(Movie._C_YEAR, date.substring(0, 4));
            }
                
            if (mdb.getRuntime() > 0) 
                movie.setValue(Movie._L_PLAYLENGTH, (mdb.getRuntime() * 60));
            
            movie.addExternalReference(ExternalReferences._TMDB, String.valueOf(mdb.getId()));
            
            if (!CoreUtilities.isEmpty(mdb.getImdbID()))
                movie.addExternalReference(ExternalReferences._IMDB, mdb.getImdbID());
            
            keys.add(movie);
        }
        return keys;
    }
}
