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

package net.datacrow.onlinesearch.amazon.task;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcMediaObject;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.Region;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.html.HtmlUtils;
import net.datacrow.onlinesearch.amazon.SignedRequestsHelper;
import net.datacrow.onlinesearch.amazon.mode.ItemLookupSearchMode;
import net.datacrow.settings.DcSettings;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class AmazonSearch extends SearchTask {

	private static Logger logger = Logger.getLogger(AmazonSearch.class.getName());
	
    public static final int _KEYWORDSSEARCH = 0;
    public static final int _ASINSEARCH = 1;
    
    private final static String AWS_ACCESS_KEY_ID = "AKIAJHLT32F2JMMQEYNQ";
    private final static String AWS_SECRET_KEY = "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";
    
    private final static XPath xpath = XPathFactory.newInstance().newXPath();
    private final static Calendar cal = Calendar.getInstance();
    
    private static SignedRequestsHelper helper;
    
    public AmazonSearch(IOnlineSearchClient listener, 
                        IServer server,
                        Region region,
                        SearchMode mode,
                        String query) {
        
        super(listener, server, region, mode, query);
        
        if (helper == null) {
            try {
                helper = new SignedRequestsHelper(AWS_ACCESS_KEY_ID, AWS_SECRET_KEY);
            } catch (InvalidKeyException e) {
                logger.error("Amazon Search failed: The provide key is invalid", e);
            } catch (UnsupportedEncodingException e) {
                logger.error("Amazon Search failed: The encoding is not supported", e);
            } catch (NoSuchAlgorithmException e) {
                logger.error("Amazon Search failed: The algorithm is not supported", e);
            }
        }
    }

    @Override
    public String getWhiteSpaceSubst() {
        return "%20";
    }

    private String convertSearchString(String s) {
        String result = s;
        if (getMode() instanceof ItemLookupSearchMode) 
            result = s.replaceAll("-","");
        return result;
    }

    protected String getSearchURL(String keywords) {
        String s = convertSearchString(keywords);
        String url = "Service=AWSECommerceService&Version=2009-03-31&" + getMode().getSearchCommand(s) + "&ResponseGroup=Small&AssociateTag=3054-0237-8289";
        return helper.sign(getAddress(), url);
    }
    
    @Override
    protected DcObject getItem(URL url) throws Exception {
        return getItem(HtmlUtils.getDocument(new URL(helper.sign(url)), "UTF-8"), true);
    }
    
    private URL getDetailURL(String asin) throws MalformedURLException {
        return new URL(helper.sign(getAddress(), 
                "Service=AWSECommerceService&Version=2009-03-31&" +  
                new ItemLookupSearchMode("", ItemLookupSearchMode.getDescription(ItemLookupSearchMode._ASIN), ItemLookupSearchMode._ASIN, -1).getSearchCommand(asin) +  
                "&ResponseGroup=Large&AssociateTag=3054-0237-8289"));
    }
    
    protected Document getDocument(String asin) throws Exception {
        
        try {
            sleep(1000);
        } catch (Exception e) {
            logger.debug(e, e);
        }
        
        return HtmlUtils.getDocument(getDetailURL(asin), "UTF-8");
    }

    protected void setServiceURL(DcObject dco, String asin) {
        try {
            dco.setValue(DcMediaObject._SYS_SERVICEURL, getDetailURL(asin));
        } catch (MalformedURLException e) {
            logger.error("The service URL is incorrect for ASIN " + asin, e);
        }
    }
    
    @Override
    protected Collection<Object> getItemKeys() throws Exception {
    	ArrayList<Object> asins = new ArrayList<Object>();
    	try {
            Document document = HtmlUtils.getDocument(new URL(getSearchURL(getQuery())), "ISO-8859-1");
            NodeList list = (NodeList) xpath.evaluate("//Item/ASIN", document, XPathConstants.NODESET);
            
            
            if (list != null) {
                for (int i = 0; i < list.getLength(); i++) {
                    String asin = list.item(i).getTextContent();
                    asins.add(asin);
                }
            }
    	} catch (Exception e) {
    		logger.error("An error occured while parsing the Amazon result.", e);
    	}
        return asins;
    }
    
    protected String getValue(Document document, String tag) {
        try {
            Node node =(Node) xpath.evaluate("//Items/Item/" + tag + "[1]", document, XPathConstants.NODE);
            return node != null ? node.getTextContent() : null;
        } catch (Exception e) {
            logger.error("Error while retrieving " + tag, e);
        }
        return null;
    }
    
    protected NodeList getNodeList(Document document, String tag) {
        try {
            return (NodeList) xpath.evaluate("//Items/Item/" + tag, document, XPathConstants.NODESET);
        } catch (Exception e) {
            logger.error("Error while retrieving " + tag, e);
        }
        return null;
    }
    
    protected Collection<String> getValues(Document document, String tag) {
        Collection<String> values = new ArrayList<String>();
        try {
            NodeList nodes = getNodeList(document, tag);
            for (int i = 0; nodes != null && i < nodes.getLength(); i++)
                values.add(nodes.item(i).getTextContent());
        } catch (Exception e) {
            logger.error("Error while retrieving " + tag, e);
        }
        return values;
    }    
    
    protected void setYear(Document document, String tag, DcObject dco, int fieldIdx) {
        try {
            String value = getValue(document, tag);
            if (value != null) dco.setValue(fieldIdx, getYear(value));
        } catch (Exception e) {
            logger.error("Error while retrieving " + tag, e);
        }
    }    
    
    protected void setValue(Document document, String tag, DcObject dco, int fieldIdx) {
        try {
            Node node =(Node) xpath.evaluate("//Items/Item/" + tag + "[1]", document, XPathConstants.NODE);
            if (node != null) {
                String value = node.getTextContent();
                
                while (value.length() > 1 && (value.startsWith("\r") || value.startsWith("\n")))
                    value = value.substring(1);

                while (value.length() > 1 && (value.endsWith("\r") || value.endsWith("\n")))
                    value = value.substring(0, value.length() - 1);
                
                if (fieldIdx == DcObject._SYS_EXTERNAL_REFERENCES) {
                    dco.addExternalReference(DcRepository.ExternalReferences._ASIN, value);
                } else if (dco.getField(fieldIdx).getValueType() == DcRepository.ValueTypes._DCOBJECTCOLLECTION ||
                    dco.getField(fieldIdx).getValueType() == DcRepository.ValueTypes._DCOBJECTREFERENCE) {
                    dco.createReference(fieldIdx, value);
                } else {
                    dco.setValue(fieldIdx, value);
                }
            }
        } catch (Exception e) {
            logger.error("Error while retrieving " + tag, e);
        }
    }
    
    protected void setRating(Document document, DcObject dco, int fieldIdx) {
        String s = getValue(document, "CustomerReviews/AverageRating");
        try {
            if (s != null && s.trim().length() > 0) {
                float f = Float.valueOf(s) * 2;
                dco.setValue(fieldIdx, Long.valueOf(Math.round(f)));
            }
        } catch (Exception e) {
            logger.debug("Could not get rating. Value invalid : " + s);
        }
    }    
    
    protected void setLanguage(Document document, DcObject dco, int fieldIdx) {
        String language = getValue(document, "ItemAttributes/Languages/Language[Type='Original Language']/Name");
        language = language == null ? getValue(document, "ItemAttributes/Languages/Language[Type='original']/Name") : language;
        if (language != null)
            dco.createReference(fieldIdx, language);
    }    
    
    protected void setDescription(Document document, DcObject dco, int fieldIdx) {
        StringBuffer sb = new StringBuffer();
        
        // features
        if (DcSettings.getBoolean(DcRepository.Settings.stAmazonRetrieveFeatureListing)) {
            try {
                NodeList list =(NodeList) xpath.evaluate("//Item/ItemAttributes/Feature", document, XPathConstants.NODESET);
                for (int i = 0; list != null && i < list.getLength(); i++) {
                    sb.append(HtmlUtils.toPlainText(list.item(i).getTextContent()));
                    if (!sb.toString().endsWith(".")) sb.append(".");
                    sb.append("\n");
                }
            } catch (Exception e) {
                logger.error("Error while retrieving features", e);
            }
        }
        
        // editorials
        if (DcSettings.getBoolean(DcRepository.Settings.stAmazonRetrieveEditorialReviews)) {
            try {
                NodeList list =(NodeList) xpath.evaluate("//Item/EditorialReviews/EditorialReview/Content", document, XPathConstants.NODESET);
                for (int i = 0; list != null && i < list.getLength(); i++) {
                    String s =  HtmlUtils.toPlainText(list.item(i).getTextContent());
                    sb.append(sb.length() > 0 ? "\n\n" : "");
                    sb.append(s);
                }
            } catch (Exception e) {
                logger.error("Error while retrieving editorial reviews", e);
            }
        }
        
        // user reviews
        if (DcSettings.getBoolean(DcRepository.Settings.stAmazonRetrieveUserReviews)) {
            try {
                NodeList list =(NodeList) xpath.evaluate("//Item/CustomerReviews/Review/Content", document, XPathConstants.NODESET);
                for (int i = 0; i < list.getLength(); i++) {
                    String s =  HtmlUtils.toPlainText(list.item(i).getTextContent());
                    sb.append(sb.length() > 0 ? "\n\n" : "");
                    sb.append(s);
                }
            } catch (Exception e) {
                logger.error("Error while retrieving customer reviews", e);
            }
        }
        
        String description = sb.toString();
        description = description.startsWith("\n") ? description.substring(1) : description;
        dco.setValue(fieldIdx, description);
    }

    protected Long getYear(String s) {
        Long year = null;
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd").parse(s);
            cal.setTime(date);
            year = Long.valueOf(cal.get(Calendar.YEAR));
        } catch (Exception ignore) {
            try {
                if (s.length() >= 4)
                    year = Long.valueOf(s.substring(0, 4));
            } catch (Exception e) {
                logger.warn("Could not get year. Value invalid : " + s, e);
            }
        }
        return year;
    }
    
    protected void setImages(DcObject dco, Document document, int fieldIdx, int[] others) {
        String url = getValue(document, "LargeImage/URL");
        if (url != null) {
            try {
                dco.setValue(fieldIdx, new DcImageIcon(HttpConnectionUtil.retrieveBytes(url)));
            } catch (Exception e) {
                logger.error("Primary image could not be retrieved", e);
            }
        }

        if (others != null) {
            try {
                NodeList variantList =(NodeList) xpath.evaluate("//ImageSets/ImageSet[@Category='variant']/LargeImage/URL", document, XPathConstants.NODESET);
                for (int i = 0; variantList != null && i < variantList.getLength() && i < others.length; i++) {
                    url = variantList.item(i).getTextContent();
                    if (url != null)
                        dco.setValue(others[i], new DcImageIcon(HttpConnectionUtil.retrieveBytes(url)));
                }
            } catch (Exception e) {
                logger.error("Additional images could not be retrieved", e);
            }
        }
    }
}
