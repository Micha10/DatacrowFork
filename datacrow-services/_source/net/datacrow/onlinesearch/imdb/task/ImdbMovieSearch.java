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

package net.datacrow.onlinesearch.imdb.task;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import net.datacrow.core.DcConfig;
import net.datacrow.core.DcRepository;
import net.datacrow.core.modules.DcModules;
import net.datacrow.core.objects.DcAssociate;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Movie;
import net.datacrow.core.server.Connector;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.Region;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.settings.Setting;
import net.datacrow.core.utilities.CoreUtilities;
import net.datacrow.core.utilities.StringUtils;
import net.datacrow.core.utilities.html.HtmlUtils;
import net.datacrow.settings.DcSettings;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ImdbMovieSearch extends ImdbSearch {
    
    private static Logger logger = Logger.getLogger(ImdbMovieSearch.class.getName());
    
    private final static XPath xpath = XPathFactory.newInstance().newXPath();
    
    private final static int MAX = 20;
    
    public ImdbMovieSearch(IOnlineSearchClient listener, 
                           IServer server, 
                           Region region,
                           SearchMode mode,
                           String query) {

        super(listener, server, region, mode, query);
    }
    
    @Override
    public DcObject getItem(Object key, boolean full) throws Exception {
        URL url = new URL(getAddress() + "/title/tt" + key);
        return getItem(url, full);
    }      
    
    private boolean isUseAlternativeTitle() {
        boolean useAlternativeTitle = false;
        for (Setting setting :  getServer().getSettings()) {
            useAlternativeTitle = setting.getKey().equals(DcRepository.Settings.stImdbGetOriginalTitle) ?
                    ((Boolean) setting.getValue()).booleanValue() : useAlternativeTitle;
                        
        }
        return useAlternativeTitle;
    }
    
    @Override
    public DcObject getItem(URL url) throws Exception {
        return getItem(url, true, isUseAlternativeTitle());
    }
    
    public DcObject getItem(URL url, boolean full) throws Exception {
        return getItem(url, full, isUseAlternativeTitle());
    }
    
    public DcObject getItem(URL url, boolean full, boolean alternativeTitle) throws Exception {

        DcObject movie = DcModules.get(DcModules._MOVIE).getItem();
        String id = "";
        
        try {
            Document document = HtmlUtils.getDocument(url, "UTF8", 0);
            
            String serviceURL = url.toString();
            id = serviceURL.substring(serviceURL.indexOf("/title/tt") + 9, serviceURL.length());

            movie.setValue(Movie._G_WEBPAGE, getAddress() + "/title/tt" + id);
            movie.setValue(DcObject._SYS_SERVICEURL, serviceURL);
            movie.addExternalReference(DcRepository.ExternalReferences._IMDB, "tt" + id);

            ImdbApiSearch ias = new ImdbApiSearch(movie, id, getRegion(), isUseAlternativeTitle());
            ias.query();
            ias.destroy();
            
            if (full) {
                Object actors = movie.getValue(Movie._I_ACTORS);
                movie.setValueLowLevel(Movie._I_ACTORS, null);
                setActors(id, movie);
                if (!movie.isFilled(Movie._I_ACTORS))
                    movie.setValueLowLevel(Movie._I_ACTORS, actors);
                
                Object directors = movie.getValue(Movie._J_DIRECTOR);
                movie.setValueLowLevel(Movie._J_DIRECTOR, null);
                setDirectors(document, movie);
                if (!movie.isFilled(Movie._J_DIRECTOR))
                    movie.setValueLowLevel(Movie._J_DIRECTOR, directors);
                
                setColor(document, movie);
            }
        } catch (ImdbApiServiceExpection e) {
            logger.info("Skipping item " + id);
            logger.debug(e, e);
            movie = null;
        } catch (Exception e) {
            logger.error(e, e);
        }

        return movie;
    }
    
    @Override
    public Collection<Object> getItemKeys() throws Exception {
        URL url = new URL(getAddress() + "/find?s=tt&q=" + getQuery());
        Document document = HtmlUtils.getDocument(url, "UTF8");
        NodeList nlKeys = (NodeList) xpath.evaluate("//a[@href[starts-with(., '/title/tt')]]", document, XPathConstants.NODESET);
        Collection<Object> keys = new ArrayList<Object>();
        for (int i = 0; nlKeys != null && i < nlKeys.getLength(); i++) {
            Node node = nlKeys.item(i);
            Node href = node.getAttributes().getNamedItem("href");
            if (href != null) {
                String key = StringUtils.getValueBetween("/title/tt", "/", href.getTextContent());
                if (!keys.contains(key) && keys.size() < MAX) keys.add(key);
            }
        }
        return keys;
    }
    
    private void setColor(Document document, DcObject movie) throws Exception {
        String text = getText(document, getTag(_COLOR));
        if (!CoreUtilities.isEmpty(text)) {
        	movie.createReference(Movie._13_COLOR, text);
        }
    }        

    private void setDirectors(Document document, DcObject movie) throws Exception {
        String syntax = "//div[contains(h4,'" + getTag(_DIRECTOR) + "')]/a";
        Node director = (Node) xpath.evaluate(syntax, document, XPathConstants.NODE);
        syntax = "//div[contains(h5,'" + getTag(_DIRECTOR) + "')]/a";
        director = director == null ? (Node) xpath.evaluate(syntax, document,XPathConstants.NODE) : director;

        if (director != null) {
            String name = director.getTextContent();
            String path = director.getAttributes().getNamedItem("href").getTextContent();
            String id = StringUtils.getValueBetween("name/nm", "/", path);
            try {
                DcObject person = getConcretePerson("nm" + id, name, DcModules._DIRECTOR);
                movie.createReference(Movie._J_DIRECTOR, person);
            } catch (Exception e) {
                logger.error("Error while creating director " + name, e);
            }
        } else {
            syntax = "//div[contains(h4,'" + getTag(_DIRECTORS) + "')]/a";
            NodeList div = (NodeList) xpath.evaluate(syntax, document, XPathConstants.NODESET);
            syntax = "//div[contains(h5,'" + getTag(_DIRECTORS) + "')]/a";
            NodeList directors = div == null ? (NodeList) xpath.evaluate(syntax, document, XPathConstants.NODESET) : div;

            if (directors != null) {
                for (int i = 0; i < directors.getLength(); i++) {
                    director = directors.item(i);
                    String name = director.getTextContent();
                    String path = director.getAttributes().getNamedItem("href").getTextContent();
                    String id = StringUtils.getValueBetween("name/nm", "/", path);
                    try {
                        if (id.length() > 0) {
                            DcObject person = getConcretePerson("nm" + id, name, DcModules._DIRECTOR);
                            movie.createReference(Movie._J_DIRECTOR, person);
                        }
                    } catch (Exception e) {
                        logger.error("Error while creating director " + name, e);
                    }
                }
            }
        }
    } 
    

    private void setActors(String movieId, DcObject movie) throws Exception {
        Document document = HtmlUtils.getDocument(new URL(getAddress() + "/title/tt" + movieId + "/fullcredits#cast"), "UTF8");
        NodeList nlActors = (NodeList) xpath.evaluate("//table[@class='cast']//a[@href[starts-with(., '/name/nm')]]", document, XPathConstants.NODESET);
        
        int max = DcSettings.getInt(DcRepository.Settings.stImdbMaxActors);
        
        int counter = 0;
        for (int i = 0; nlActors != null && i < nlActors.getLength(); i++) {
            
            if (counter == max && max != 0)
                break;
            
            try {
                Node node = nlActors.item(i);
                Node href = node.getAttributes().getNamedItem("href");
                if (href != null) {
                    String id = StringUtils.getValueBetween("/name/nm", "/", href.getTextContent());
                    String name = node.getTextContent();
                    if (!CoreUtilities.isEmpty(name)) {
                        DcObject person = getConcretePerson("nm" + id, name, DcModules._ACTOR);
                        movie.createReference(Movie._I_ACTORS, person);
                        counter++;
                    }
                }
            } catch (Exception e) {
                logger.error("Error while creating actor", e);
            }                
        }
    }
    
    /**
     * Queries Imdb for the specific person (using the imdb id). Then it checks if the
     * person exists already in the database. If not, it is created. The new or existing
     * person is then returned.
     * @throws Exception
     */
    private DcObject getConcretePerson(String imdbId, String name, int module) throws Exception {
        
        Connector connector = DcConfig.getInstance().getConnector();
        DcObject person = connector.getItemByExternalID(module, DcRepository.ExternalReferences._IMDB, imdbId);
        person = person == null ? connector.getItemByKeyword(module, name) : person;
        
        if (person == null) {
            if (DcModules.get(DcModules._MOVIE).getSettings().getBoolean(DcRepository.ModuleSettings.stOnlineSearchSubItems)) {
                try {
                    ImdbPerson imdbPerson = new ImdbPerson(module, true);
                    person = imdbPerson.get(imdbId);
                    sleep(100);
                } catch (Exception e) {
                    logger.error("Could not retrieve person details from IMDB for person " + name + " with id " + imdbId, e);
                    person = DcModules.get(module).getItem();
                    person.setValue(DcAssociate._A_NAME, name);
                }
            } else {
                person = DcModules.get(module).getItem();
                person.setValue(DcAssociate._A_NAME, name);
            }
            
            person.addExternalReference(DcRepository.ExternalReferences._IMDB, imdbId);
            person.setIDs();
        }
        return person;
    }    
    
    private String getText(Document document, String tag) throws Exception {
        Node node = getNode(document, tag, true);
        node = node == null ? getNode(document, tag, false) : node;
        
        String text = null;
        if (node != null) {
            text = node.getTextContent();
            text = clean(text, tag);
            text = StringUtils.trim(text); 
         }
        
        return text;
    }    
    
    private Node getNode(Document document, String tag, boolean href)throws Exception {
        String syntax = "//div[h4='" + tag + "']" + (href ? "/a" : "");
        Node div = (Node) xpath.evaluate(syntax, document, XPathConstants.NODE);
        syntax = "//div[h5='" + tag + "']" + (href ? "/a" : "");
        return div == null ? (Node) xpath.evaluate(syntax, document, XPathConstants.NODE) : div;
    }
    
    private String clean(String s, String tag) {
        String value = s;

        if (value.indexOf(tag) > -1)
            value = s.substring(value.indexOf(tag) + tag.length());

        value = value.replaceAll("\n|\t|\r", "");

        if (value.indexOf(getTag(_MORE)) > -1)
            value = value.substring(0, value.indexOf(getTag(_MORE)));

        return value;
    } 

//    private void setDescription(String id, DcObject movie) {
//        try {
//            Document document = HtmlUtils.getDocument(new URL(getAddress() + "/Plot?" + id), "ISO-8859-1");
//            Node p = (Node) xpath.evaluate("//p[@class='plotpar']", document, XPathConstants.NODE);
//            if (p != null) {
//                String description = p.getTextContent();
//                while (description.startsWith("\n") || description.startsWith("\r"))
//                    description = description.substring(1);
//                
//                movie.setValue(Movie._B_DESCRIPTION, description);
//            }
//        } catch (Exception e) {
//            logger.error(e, e);
//        }
//    }
    
//    private void setPoster(Document document, String id, DcObject movie) throws Exception {
//        Node node = (Node) xpath.evaluate("//td[@id='img_primary']//img", document, XPathConstants.NODE);
//        node = node == null ? (Node) xpath.evaluate("//a[@name='poster']/img", document, XPathConstants.NODE) : node;
//        if (node != null) {
//            byte[] image = null;
//        	String link = node.getAttributes().getNamedItem("src").getTextContent();
//        	
//        	if (link.indexOf("nopicture") == -1) {
//	            image = HttpConnectionUtil.retrieveBytes(link);
//	            if (image.length > 100 && image != null)
//	            	movie.setValue(Movie._X_PICTUREFRONT, new DcImageIcon(image));
//        	}
//        }
//    }
//
//    private String clean(String s, String tag) {
//        String value = s;
//        
//        if (value.indexOf(tag) > -1)
//        	value = s.substring(value.indexOf(tag) + tag.length());
//        
//        value = value.replaceAll("\n|\t|\r", "");
//        
//        if (value.indexOf(getTag(_MORE)) > -1)
//        	value = value.substring(0, value.indexOf(getTag(_MORE)));
//        
//        return value;
//    }
//    
//  private void setTitleAndYear(Document document, DcObject movie, String ID, boolean useAlternativeTitle) throws Exception {
//  
//  Node node = (Node) xpath.evaluate("//title", document, XPathConstants.NODE);
//  
//  if (node == null) return;
//  
//  String title = node.getTextContent();
//  title = title.toLowerCase().indexOf("- imdb") > -1 ? title.substring(0, title.toLowerCase().indexOf("- imdb")) : title;
//  title = title.toLowerCase().indexOf("imdb -") > -1 ? title.substring(title.toLowerCase().indexOf("imdb -") + 6) : title;
//  
//  title = StringUtils.trim(title);
//  movie.setValue(Movie._A_TITLE, title);
//  for (String year : StringUtils.getValuesBetween("(", ")", title)) {
//      try {
//          if (year.indexOf("/") > -1) year = year.substring(0, year.indexOf("/"));
//          movie.setValue(Movie._C_YEAR, Long.parseLong(year));
//          break;
//      } catch (NumberFormatException nfe) {}
//  }
//  
//  if (useAlternativeTitle) {
//      DcObject movieUS = getItem(new URL("http://www.imdb.com/title/tt" + ID), false, false);
//      movie.setValue(Movie._F_TITLE_LOCAL, movie.getValue(Movie._A_TITLE));
//      movie.setValue(Movie._A_TITLE, movieUS.getValue(Movie._A_TITLE));
//  }
//}
    
//  private void setCertification(Document document, DcObject movie) throws Exception {
//  String certification = getText(document, getTag(_CERTIFICATION));
//  if (!Utilities.isEmpty(certification)) {
//      certification = clean(certification, getTag(_CERTIFICATION));
//      certification = certification.replaceAll("\\|", ", ");
//      certification = certification.replaceAll(" ,", ",");
//      certification = certification.replaceAll(" ,", ",");
//      certification = certification.replaceAll("\n", "");
//      movie.setValue(Movie._3_CERTIFICATION, certification);       
//  }
//}
//
//private void setCountries(Document document, DcObject movie) throws Exception {
//  String syntax = "//div[h4='" + getTag(_COUNTRY) + "']";
//  Node div = (Node) xpath.evaluate(syntax, document, XPathConstants.NODE);
//  
//  if (div == null) return;
//
//  String countries = div.getTextContent();
//  if (!Utilities.isEmpty(countries)) {
//      countries = clean(countries, getTag(_COUNTRY));
//      StringTokenizer st = new StringTokenizer(countries, "|");
//      while (st.hasMoreElements()) {
//          String country = ((String) st.nextElement()).trim();
//          DataManager.createReference(movie, Movie._F_COUNTRY, country);
//      }
//  }
//}

//private void setRating(Document document, DcObject movie) throws Exception {
//Node div = getNode(document, getTag(_USERRATING), false);
//
//if (div == null) {
//    
//    String syntax = "//div[@class='titlePageSprite star-box-giga-star']";
//    div = (Node) xpath.evaluate(syntax, document, XPathConstants.NODE);
//    
//    if (div == null) return;
//    
//    String rating = div.getTextContent();
//    rating = rating.replace(",", ".");
//    
//    if (rating.indexOf("/") > -1)
//        rating = rating.substring(0, rating.indexOf("/"));
//    
//    rating = rating.trim();
//    
//      try {
//          int value = Math.round(Float.valueOf(rating));
//          movie.setValue(Movie._E_RATING, value);
//      } catch (NumberFormatException nfe) {
//          logger.debug("Could not create rating from " + rating + " for " + movie);
//      }
//} else {
//    String rating = div.getTextContent();
//    if (!Utilities.isEmpty(rating)) {
//        rating = StringUtils.getValueBetween(getTag(_USERRATING), "/", rating);
//        rating = rating.replaceAll("\n|\t|\r", "");
//        rating = rating.replace(",", ".");
//        try {
//            int value = Math.round(Float.valueOf(rating));
//            movie.setValue(Movie._E_RATING, value);
//        } catch (NumberFormatException nfe) {
//            logger.debug("Could not create rating from " + rating + " for " + movie);
//        }
//    }
//}
//}



//private void setLanguages(Document document, DcObject movie) throws Exception {
//  Node node = getNode(document, getTag(_LANGUAGE), true);
//  if (node != null) {
//      String languages = node.getParentNode().getTextContent();
//      languages = languages.substring(1);
//      if (languages.indexOf("\n") > 0) {
//          languages = languages.substring(languages.indexOf("\n"));
//          languages = languages.startsWith("\n") ? languages.substring(1) : languages;
//          languages = StringUtils.trim(languages);
//          
//          StringTokenizer st = new StringTokenizer(languages, "|");
//          while (st.hasMoreElements()) {
//              String language = ((String) st.nextElement()).trim();
//              
//              if (language.indexOf(" ") > 0)
//                  language = language.substring(0, language.indexOf(" ")).trim();
//              
//              DataManager.createReference(movie, Movie._D_LANGUAGE, language.trim());
//          }
//      }
//  }
//}     



//private void setAspectRatio(Document document, DcObject movie) throws Exception {
//  String text =  getText(document, getTag(_ASPECT_RATIO));
//  if (!Utilities.isEmpty(text)) DataManager.createReference(movie, Movie._14_ESPECT_RATIO, text);
//}

//private void setRuntime(Document document, DcObject movie) throws Exception {
//  String text = getText(document, getTag(_RUNTIME));
//  if (!Utilities.isEmpty(text)) {
//      text = text.indexOf("|") > -1 ? text.substring(0, text.indexOf("|")) : text;
//      text = text.indexOf("(") > -1 ? text.substring(0, text.indexOf("(")) : text;
//      String duration = StringUtils.getContainedNumber(text);
//      if (!Utilities.isEmpty(duration))
//          movie.setValue(Movie._L_PLAYLENGTH, Long.valueOf(duration) * 60);
//  }
//}

//private void setGenres(Document document, DcObject movie) throws Exception {
//NodeList genres =  getNodeList(document, getTag(_GENRE), true);
//  if (genres != null && genres.getLength() > 0) {
//      // skip the last one (the 'more' link)
//      for (int i = 0; i < genres.getLength(); i++) {
//          Node genre = genres.item(i);
//          String s = StringUtils.trim(genre.getTextContent());
//          s = clean(s, "NOTNEEDED");
//          DataManager.createReference(movie, Movie._H_GENRES, s);
//      }
//  } else {
//      String text = getText(document, getTag(_GENRE));
//      if (text != null) {
//          StringTokenizer st = new StringTokenizer(text, "|");
//          while (st.hasMoreElements())
//              DataManager.createReference(movie, Movie._H_GENRES, ((String) st.nextElement()).trim());
//      }
//  }
//}    
}
