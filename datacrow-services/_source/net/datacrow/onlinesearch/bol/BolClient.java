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

package net.datacrow.onlinesearch.bol;

import net.datacrow.util.HttpOAuthHelper;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

//import sun.misc.IOUtils;

public class BolClient {

    private static final String accessKeyId = "F1C7F76681854D37875C5343E9B691E5";
    private static final String secretAccessKey = "E35C352CF3740519FBC488C1D0904754347A961B9664D0B16BBE24B6EADA22A3E5648C28B16AB6982DB286BF86FB4CCDF8FB250A6093B40BD514E519E961A053A5D1FD5E78EB94B9317D7DE375D757DF2269CD99396D502C605DBBFAD8276B36ECF137178C16D117C5EF36E9306B7A08E2A6C8AA287344A40160FCAE4B781103";
    
    private HttpClient httpClient = new DefaultHttpClient();
    
    /**
     * Constructs the test client.
     */
    public BolClient() {}

    /**
     * Searches.
     * 
     * @param term The search term (required).
     * @param categoryID The category id and refinements, separated by spaces (optional).
     */
    public String search (String term, String categoryID) throws IOException, URISyntaxException {
        List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
        queryParams.add(new BasicNameValuePair("term", term));

        if (categoryID != null)
            queryParams.add(new BasicNameValuePair("categoryId", categoryID));
        
        queryParams.add(new BasicNameValuePair("nrProducts", "10"));
        queryParams.add(new BasicNameValuePair("includeProducts", "TRUE"));
        queryParams.add(new BasicNameValuePair("includeCategories", "TRUE"));
        queryParams.add(new BasicNameValuePair("includeRefinements", "FALSE"));

        URIBuilder builder = new URIBuilder();
        builder.setScheme("https");
        builder.setHost("openapi.bol.com");
        builder.setPath("/openapi/services/rest/catalog/v3/searchresults/");
        builder.setQuery(URLEncodedUtils.format(queryParams, "UTF-8"));
                   
        HttpGet httpGet = new HttpGet(builder.build());
        
        HttpOAuthHelper au = new HttpOAuthHelper("application/xml");
        au.handleRequest(httpGet, accessKeyId, secretAccessKey, null, queryParams);

        HttpResponse httpResponse = httpClient.execute(httpGet);
        String xml = getXML(httpResponse);
        httpClient.getConnectionManager().shutdown();
        return xml;
    }

    /**
     * Gets the product.
     * @param id The product id (required).
     */
    public String getProduct(String ID) throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https");
        builder.setHost("openapi.bol.com");
        builder.setPath("/openapi/services/rest/catalog/v3/products/" + ID);
        URI uri = builder.build();

        HttpGet httpGet = new HttpGet(uri);
        
        HttpOAuthHelper au = new HttpOAuthHelper("application/xml");
        au.handleRequest(httpGet, accessKeyId, secretAccessKey);

        HttpResponse httpResponse = httpClient.execute(httpGet);
        String xml = getXML(httpResponse);
        httpClient.getConnectionManager().shutdown();
        return xml;
    }
    
    private String getXML(HttpResponse response) throws IOException {
        String xml = "";
        InputStream is = response.getEntity().getContent();
        String text = null;
        try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            text = scanner.useDelimiter("\\A").next();
        }
        //xml = new String(IOUtils.readFully(is, -1, true), "UTF8");
        xml=text;
        is.close();
        return xml;
    }
}
