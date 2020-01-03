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

package net.datacrow.onlinesearch.google.task;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnection;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Book;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.SearchTaskUtilities;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.StringUtils;

import org.apache.log4j.Logger;

public class GoogleSearch extends SearchTask {

    private static Logger logger = Logger.getLogger(GoogleSearch.class.getName());

    public GoogleSearch(IOnlineSearchClient listener, IServer server, SearchMode mode, String query) {
        super(listener, server, null, mode, query);
    }

    @Override
    protected DcObject getItem(Object key, boolean full) throws Exception {
        return (DcObject) key;
    }

    @Override
    protected DcObject getItem(URL url) throws Exception {
        return null;
    }

    @Override
    public String getWhiteSpaceSubst() {
        return "+";
    }

    @Override
    protected void preSearchCheck() {
        SearchTaskUtilities.checkForIsbn(this);
    }
    
    private void setDescription(String googleBook, Book book) {
        String description = getValue("subtitle", googleBook);
        
        String text = getValue("description", googleBook);
        text = text == null || text.length() == 0 ? getValue("textSnippet", googleBook) : text;
        if (text != null && text.length() > 0) {
            description += description.length() > 0 ? "\n\n" : "";
            description += text;
        }

        book.setValue(Book._B_DESCRIPTION, description);
    }
    
    private void setYear(String googleBook, Book book) {
        String publishedDate = getValue("publishedDate", googleBook);
        if (publishedDate != null && publishedDate.length() > 0) {
            try {
                publishedDate = publishedDate.contains("-") ? publishedDate.substring(0, publishedDate.indexOf("-")) : publishedDate;
                book.setValue(Book._C_YEAR, Long.valueOf(publishedDate));
            } catch (Exception e) {
                logger.debug("Could not parse publishdate for " + book + ", value: " + publishedDate, e);
            }
        }
    }

    private void setRating(String googleBook, Book book) {
        String averageRating = getValue("averageRating", googleBook);
        if (averageRating != null && averageRating.length() > 0) {
            try {
                float rating = Float.valueOf(averageRating) * 2;
                book.setValue(Book._E_RATING, Math.round(Float.valueOf(rating)));
            } catch (Exception e) {
                logger.debug("Could not parse rating for " + book, e);
            }
        }
    }
    
    private void setIsbn(String googleBook, Book book) {
        String industryIdentifiers = getValue("industryIdentifiers", googleBook);
        if (industryIdentifiers != null && industryIdentifiers.contains("ISBN_13")) {
            String isbn13 = industryIdentifiers.substring(industryIdentifiers.indexOf("ISBN_13"));
            isbn13 = getValue("identifier", isbn13);
            book.setValue(Book._N_ISBN13, isbn13);
        }
    }
    
    private void setAuthors(String googleBook, Book book) {
        String authors = getValue("authors", googleBook);
        if (authors != null && authors.length() > 0) {
            
            for (String author : StringUtils.getValuesBetween("\"", "\"", authors)) {
                
                if (author.startsWith(",") || author.length() == 0) continue;
                
                book.createReference(Book._G_AUTHOR, author);
            }
        }
    }
    
    private void setCategories(String googleBook, Book book) {
        String categories = getValue("categories", googleBook);
        if (categories != null && categories.length() > 0) {
            for (String category : StringUtils.getValuesBetween("\"", "\"", categories)) {
                
                if (category.startsWith(",") || category.length() == 0) continue;
                
                book.createReference(Book._I_CATEGORY, category);
            }
        }
    }
    
    private void setPages(String googleBook, Book book) {
        String pageCount = getValue("pageCount", googleBook);
        if (pageCount != null && pageCount.length() > 0) {
            try {
                book.setValue(Book._T_NROFPAGES, Long.valueOf(pageCount));
            } catch (NumberFormatException nfe) {
                logger.debug("Cannot determine the number of pages for " + book + ", value " + pageCount, nfe);
            }
        }
    }
    
    private void setImages(String googleBook, Book book) {
        String link = getValue("thumbnail", googleBook);
        try {
            if (link != null && link.length() > 0) {
                byte[] b = HttpConnectionUtil.retrieveBytes(link);
                if (b != null && b.length > 50)
                    book.setValue(Book._K_PICTUREFRONT, b);
            }
        } catch (Exception e) {
            logger.debug("Cannot download image for " + book + ", value " + link, e);
        }
    }
    
    @Override
    protected Collection<Object> getItemKeys() throws Exception {
        Collection<Object> keys = new ArrayList<Object>();

        URL url = new URL("https://www.googleapis.com/books/v1/volumes?q=" + getQuery());

        HttpConnection connection = HttpConnectionUtil.getConnection(url);
        String result = connection.getString("UTF-8");
        
        if (logger.isDebugEnabled()) {
            //Utilities.writeToFile(result.getBytes(), "google_online_service_document.txt");
        }
     
        Collection<String> googleBooks = StringUtils.getValuesBetween("\"books#volume\"", "\"books#volume\"", result);

        Book book;
        for (String googleBook : googleBooks) {
            book = new Book();
            
            String googleID = getValue("id", googleBook);
            
            book.addExternalReference(DcRepository.ExternalReferences._GOOGLE, googleID);
            book.setValue(DcObject._SYS_SERVICEURL, getValue("selfLink", googleBook));
            book.setValue(Book._A_TITLE, getValue("title", googleBook));
            book.setValue(Book._H_WEBPAGE, "http://books.google.com/books?id=" + googleID);
            
            book.createReference(Book._F_PUBLISHER, getValue("publisher", googleBook));
            
            setDescription(googleBook, book);
            setYear(googleBook, book);
            setRating(googleBook, book);
            setIsbn(googleBook, book);
            setAuthors(googleBook, book);
            setCategories(googleBook, book);
            setPages(googleBook, book);
            setImages(googleBook, book);
            
            keys.add(book);
        }
        return keys;
    }
    
    private String getValue(String tag, String text) {
        int start = text.indexOf("\"" + tag + "\": ");
        
        String value = "";
        if (start > -1) {
            value = text.substring(start + tag.length()+ 4);
            if (value.startsWith("["))
                value = value.substring(0, value.indexOf("]"));
            else if (value.startsWith("\""))
                value = value.substring(1, value.indexOf("\n"));
            else
                value = value.substring(0, value.indexOf("\n"));
        }
        
        if (value.endsWith(",")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
        
        value = value.trim();
        
        return value;
    }
}
