package net.datacrow.onlinesearch.imdb.task;

import java.net.URL;
import java.util.StringTokenizer;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Movie;
import net.datacrow.core.services.Region;
import net.datacrow.core.utilities.StringUtils;
import net.datacrow.core.utilities.html.HtmlUtils;
import net.datacrow.settings.DcSettings;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class ImdbApiSearch {
    
    private static Logger logger = Logger.getLogger(ImdbApiSearch.class.getName());
    private final static XPath xpath = XPathFactory.newInstance().newXPath();
    
    private DcObject movie;
    private String imdbID;
    private boolean useAlternateTitles;
    private Region region;
    
    public ImdbApiSearch(DcObject movie, String imdbID, Region region, boolean useAlternateTitles) {
        this.movie = movie;
        this.imdbID = imdbID;
        this.useAlternateTitles = useAlternateTitles;
        this.region = region;
    }
    
    public void destroy() {
        this.region = null;
        this.movie = null;
        this.imdbID = null;
    }
    
    public void query() throws ImdbApiServiceExpection {
        URL url = null;
        try {
            url = new URL("http://www.omdbapi.com/?i=tt" + imdbID + "&plot=full&r=xml&apikey=cd2f6d4b");
            Document document = HtmlUtils.getDocument(url, "UTF8", 0);
            
            setRating(document);
            setGenres(document);
            setCertification(document);
            setYear(document);
            setTitles(document);
            setDescription(document);
            setActors(document);
            setDirectors(document);
            setImage(document);
//            setLanguages(document);
//            setCountries(document);
            setRunTime(document);
            //setAspectRatio(document);
            
        } catch (Exception e) {
            throw new ImdbApiServiceExpection(e, "An error occurred while retrieving information from " + url);
        }
    }
    
//    private void setLanguages(Document document) throws Exception {
//        NodeList languages = (NodeList) xpath.evaluate("//IMDBDocumentList/item/language/item", document, XPathConstants.NODESET);
//        if (languages != null && languages.getLength() > 0) {
//            for (int i = 0; i < languages.getLength(); i++) {
//                DataManager.createReference(movie, Movie._D_LANGUAGE, languages.item(i).getTextContent());
//            }
//        }
//    }
    
//    private void setCountries(Document document) throws Exception {
//        NodeList countries = (NodeList) xpath.evaluate("//IMDBDocumentList/item/country/item", document, XPathConstants.NODESET);
//        if (countries != null && countries.getLength() > 0) {
//            for (int i = 0; i < countries.getLength(); i++) {
//                DataManager.createReference(movie, Movie._F_COUNTRY, countries.item(i).getTextContent());
//            }
//        }
//    }
    
    private void setActors(Document document) throws Exception {
        Node actors = (Node) xpath.evaluate("//@actors", document, XPathConstants.NODE);
        
        int max = DcSettings.getInt(DcRepository.Settings.stImdbMaxActors);
        
        if (actors != null) {
            StringTokenizer st = new StringTokenizer(actors.getTextContent(), ",");
            String s;
            int counter = 0;
            while (st.hasMoreElements() && (max <= 0 || counter < max)) {
                s = (String) st.nextElement();
                movie.createReference(Movie._I_ACTORS, StringUtils.trim(s));
                counter++;
            }
        }
    }
    
    private void setDirectors(Document document) throws Exception {
        Node director = (Node) xpath.evaluate("//@director", document, XPathConstants.NODE);
        if (director != null)
            movie.createReference(Movie._J_DIRECTOR, director.getTextContent());
    }
    
    private void setDescription(Document document) throws Exception {
        Node node = (Node) xpath.evaluate("//@plot", document, XPathConstants.NODE);
        if (node != null) movie.setValue(Movie._B_DESCRIPTION, node.getTextContent());
    }
    
    private void setRunTime(Document document) throws Exception {
        Node node = (Node) xpath.evaluate("//@runtime", document, XPathConstants.NODE);
        if (node != null) {
            String duration = StringUtils.getContainedNumber(node.getTextContent());
            
            if (duration.indexOf("h") > -1) {
                try {
                    int hours = Integer.parseInt(duration.substring(0, duration.indexOf("h")).trim()) * 60;
                    int minutes = 0;
                    
                    if (duration.indexOf("min") > -1)
                        minutes = Integer.parseInt(duration.substring(duration.indexOf("h") + 1, duration.indexOf("min")).trim());
                    
                    movie.setValue(Movie._L_PLAYLENGTH, Long.valueOf(hours + minutes) * 60);
                } catch (Exception e) {
                    logger.error("Could not set " + duration + " as runtime", e);
                }
            }
        }
    }
    
    private void setImage(Document document) throws Exception {
        Node node = (Node) xpath.evaluate("//@poster", document, XPathConstants.NODE);
        
        if (node != null) {
            String address = node.getTextContent();
            try {
                if (address.toLowerCase().startsWith("http://") || address.toLowerCase().startsWith("https://")) {
                     byte[] image = HttpConnectionUtil.retrieveBytes(address);
                     movie.setValue(Movie._X_PICTUREFRONT, new DcImageIcon(image));
                }
            } catch (Exception e) {
                logger.error("Could not retrieve image " + address, e);
            }
        }
    }
    
    private void setYear(Document document) throws Exception {
        Node node = (Node) xpath.evaluate("//@year", document, XPathConstants.NODE);
        if (node != null) {
            String year = node.getTextContent();
            if (year.length() == 5) {
                year = year.substring(0, year.length() - 1) ;
            } else if (year.length() > 4) {
                year = year.substring(year.length() - 4, year.length());
            }
            
            movie.setValue(Movie._C_YEAR, year);
        }
    }
    
    private void setRating(Document document) throws Exception {
        Node node = (Node) xpath.evaluate("//@imdbRating", document, XPathConstants.NODE);
        
        if (node != null) {
            String rating = node.getTextContent();
            
            try {
                int value = Math.round(Float.valueOf(rating));
                movie.setValue(Movie._E_RATING, value);
            } catch (NumberFormatException nfe) {
                logger.debug("Could not create rating from " + rating + " for " + movie);
            }
        }
    }
    
    private void setGenres(Document document) throws Exception {
        Node genres = (Node) xpath.evaluate("//@genre", document, XPathConstants.NODE);
        if (genres != null) {
            StringTokenizer st = new StringTokenizer(genres.getTextContent(), ",");
            String s;
            while (st.hasMoreElements()) {
                s = (String) st.nextElement();
                movie.createReference(Movie._H_GENRES, StringUtils.trim(s));
            }
        }
    }
    
    private void setCertification(Document document) throws Exception {
        Node node = (Node) xpath.evaluate("//@rated", document, XPathConstants.NODE);
        if (node != null) movie.setValue(Movie._3_CERTIFICATION, node.getTextContent());
    }
    
    private void setTitles(Document document) throws Exception {
        Node title = (Node) xpath.evaluate("//@title", document, XPathConstants.NODE);
        
        if (title == null) return;
        
        String titleEN = title.getTextContent();
        
        if (movie.isFilled(Movie._C_YEAR)) {
            titleEN += " (" + movie.getValue(Movie._C_YEAR) + ")";
        }
        
        movie.setValue(Movie._A_TITLE, titleEN);
        
        Node local = null;
        if (region.getCode().equals("it")) {
            local = (Node) xpath.evaluate("//IMDBDocumentList/item/also_known_as/item/country[text()='Italy']/ancestor::item/title", document, XPathConstants.NODE);
        } else if (region.getCode().equals("de")) {
            local = (Node) xpath.evaluate("//IMDBDocumentList/item/also_known_as/item/country[text()='Germany']/ancestor::item/title", document, XPathConstants.NODE);
        } else if (region.getCode().equals("fr")) {
            local = (Node) xpath.evaluate("//IMDBDocumentList/item/also_known_as/item/country[text()='France']/ancestor::item/title", document, XPathConstants.NODE);
        } else if (region.getCode().equals("sp")) {
            local = (Node) xpath.evaluate("//IMDBDocumentList/item/also_known_as/item/country[text()='Spain']/ancestor::item/title", document, XPathConstants.NODE);
        }
        
        if (local != null) {
            String titleLocal = local.getTextContent();
            if (movie.isFilled(Movie._C_YEAR))
                titleLocal += " (" + movie.getValue(Movie._C_YEAR) + ")";
            
            if (useAlternateTitles) {
                movie.setValue(Movie._F_TITLE_LOCAL, titleLocal);
            } else {
                movie.setValue(Movie._A_TITLE, local.getTextContent());
                movie.setValue(Movie._F_TITLE_LOCAL, titleEN);
            }
        }
    }
}
