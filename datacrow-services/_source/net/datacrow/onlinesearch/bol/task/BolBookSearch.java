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

package net.datacrow.onlinesearch.bol.task;

import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnection;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Book;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.SearchTaskUtilities;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.CoreUtilities;
import net.datacrow.onlinesearch.bol.BolClient;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class BolBookSearch extends SearchTask {

    private static Logger logger = Logger.getLogger(BolBookSearch.class.getName());

    private final static XPath xpath = XPathFactory.newInstance().newXPath();
    private final static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    public BolBookSearch(IOnlineSearchClient listener, IServer server, SearchMode mode, String query) {
        super(listener, server, null, mode, query);
    }

    @Override
    public String getWhiteSpaceSubst() {
        return " ";
    }

    @Override
    protected DcObject getItem(Object key, boolean full) throws Exception {
        Book book = (Book) key;
        return book;
    }

    @Override
    protected DcObject getItem(URL url) throws Exception {
        return null;
    }

    @Override
    protected void preSearchCheck() {
        SearchTaskUtilities.checkForIsbn(this);
    }

    private void setTitle(Book book, Document doc, int idx) throws Exception {
        Node nTitle = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Title", doc, XPathConstants.NODE);
        Node nSubTitle = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Subtitle", doc, XPathConstants.NODE);
        
        String title = nTitle.getTextContent();
        if (nSubTitle != null) {
            String subTitle = nSubTitle.getTextContent();
            title += CoreUtilities.isEmpty(subTitle) ? "" : " " + subTitle;
        }
        
        book.setValue(Book._A_TITLE, title);
    }
    
    private void setDescription(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/LongDescription", doc, XPathConstants.NODE);
        book.setValue(Book._B_DESCRIPTION, n != null ? n.getTextContent().replaceAll("<br/", "\n") : null);
    }
    
    private void setYear(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/ReleaseDate", doc, XPathConstants.NODE);
        book.setValue(Book._C_YEAR, n != null ? n.getTextContent().substring(0,4) : null);
    }
    
    private void setPublisher(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Publisher", doc, XPathConstants.NODE);
        if (n!= null) {
        	book.createReference(Book._F_PUBLISHER, n.getTextContent());
        }
    }
    
    private void setExternalReference(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Id", doc, XPathConstants.NODE);
        if (n!= null) book.addExternalReference(DcRepository.ExternalReferences._BOL, n.getTextContent());
    }
    
    private void setIsbn(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Ean", doc, XPathConstants.NODE);
        book.setValue(Book._N_ISBN13, n != null ? n.getTextContent() : null);
    }
    
    private void setBindingInfo(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/BindingDescription", doc, XPathConstants.NODE);
        if (n!= null) {
        	book.createReference(Book._U_BINDING, n.getTextContent());
        }
    }
    
    private void setPageCount(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/PageCount", doc, XPathConstants.NODE);
        book.setValue(Book._T_NROFPAGES, n != null ? n.getTextContent() : null);
    }
    
    private void setAuthors(Book book, Document doc, int idx) throws Exception {
        NodeList nl = (NodeList) xpath.evaluate("//Product["+ (idx + 1) + "]/Authors/Author/Name/text()", doc, XPathConstants.NODESET);
        
        Node node;
        for (int i = 0; nl != null && i < nl.getLength(); i++) {
            try {
                node = nl.item(i);
                book.createReference(Book._G_AUTHOR, node.getTextContent());
            } catch (Exception e) {
                logger.error("Skipping item due to severe parsing errors", e);
            }
        }
    }
    
    private void setServiceURL(Book book, Document doc, int idx) throws Exception {
        Node n = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Urls/Main", doc, XPathConstants.NODE);
        book.setValue(Book._SYS_SERVICEURL, n != null ? n.getTextContent() : null);
        book.setValue(Book._H_WEBPAGE, n != null ? n.getTextContent() : null);
    }
    
    private void setImage(Book book, Document doc, int idx) throws Exception {
        Node n4 = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Images/ExtraLarge", doc, XPathConstants.NODE);
        Node n3 = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Images/Large", doc, XPathConstants.NODE);
        Node n2 = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Images/Medium", doc, XPathConstants.NODE);
        Node n1 = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Images/Small", doc, XPathConstants.NODE);  
        Node n0 = (Node) xpath.evaluate("//Product["+ (idx + 1) + "]/Images/Small", doc, XPathConstants.NODE); 
        
        Node n = n4 != null ? n4 : n3 != null ? n3 : n2 != null ? n2 : n1 != null ? n1 : n0 != null ? n0 : null; 
        String url = n != null ? n.getTextContent() : null;
        if (url != null) {
            HttpConnection hc = HttpConnectionUtil.getConnection(new URL(url));
            book.setValue(Book._K_PICTUREFRONT, new DcImageIcon(hc.getBytes()));
        }
    }
    
    @Override
    protected Collection<Object> getItemKeys() throws Exception {
        Collection<Object> keys = new ArrayList<Object>();
        
        BolClient client = new BolClient();
        String xml = client.search(getQuery(), "8293");
        
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(xml)));
            
            NodeList nl = (NodeList) xpath.evaluate("//Product", doc, XPathConstants.NODESET);
            
            Book book;
            for (int i = 0; nl != null && i < nl.getLength(); i++) {
                try {
                    book = new Book();

                    setTitle(book, doc, i);
                    setDescription(book, doc, i);
                    setYear(book, doc, i);
                    setAuthors(book, doc, i);
                    setBindingInfo(book, doc, i);
                    setExternalReference(book, doc, i);
                    setIsbn(book, doc, i);
                    setPageCount(book, doc, i);
                    setPublisher(book, doc, i);
                    setYear(book, doc, i);
                    setImage(book, doc, i);
                    setServiceURL(book, doc, i);
                    
                    keys.add(book);
                } catch (Exception e) {
                    logger.error("Skipping item due to severe parsing errors", e);
                }
            }
        } catch (Exception e) {
            logger.error(e, e);
        }

        return keys;
    }
}
