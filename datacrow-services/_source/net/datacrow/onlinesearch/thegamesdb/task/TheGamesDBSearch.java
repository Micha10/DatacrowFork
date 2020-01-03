package net.datacrow.onlinesearch.thegamesdb.task;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import net.datacrow.core.DcRepository;
import net.datacrow.core.http.HttpConnectionUtil;
import net.datacrow.core.modules.DcModules;
import net.datacrow.core.objects.DcImageIcon;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.objects.helpers.Software;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.utilities.CoreUtilities;
import net.datacrow.core.utilities.StringUtils;
import net.datacrow.core.utilities.html.HtmlUtils;

import org.apache.log4j.Logger;

public class TheGamesDBSearch extends SearchTask {
    
    private static final Logger logger = Logger.getLogger(TheGamesDBSearch.class.getName());
    
    public TheGamesDBSearch(IOnlineSearchClient listener, IServer server, String query) {
        super(listener, server, null, null, query);
    }
    
    @Override
    public String getWhiteSpaceSubst() {
        return "%20";
    }
    
    @Override
    public DcObject query(DcObject dco) throws Exception {
        return getItem(dco, true);
    }
    
    @Override
    protected DcObject getItem(Object key, boolean full) throws Exception {
        DcObject game = (DcObject) key;

        if (!full) return game;
        
        try {
            String url = getAddress() + "GetGame.php?id=" + game.getExternalReference(DcRepository.ExternalReferences._THEGAMESDB);
            String result = HtmlUtils.getHtmlCleaned(new URL(url), "UTF-8", 0);
            
            String baseImgUrl = StringUtils.getValueBetween("<baseImgUrl>", "</baseImgUrl>", result);
            
            String description = StringUtils.getValueBetween("<Overview>", "</Overview>", result);
            
            Collection<String> genres = StringUtils.getValuesBetween("<genre>", "</genre>", result);
            Collection<String> screenshots = StringUtils.getValuesBetween("<screenshot>", "</screenshot>", result);
            
            String players = StringUtils.getValueBetween("<Players>", "</Players>", result);
            String coop = StringUtils.getValueBetween("<Co-op>", "</Co-op>", result);
            String publisher = StringUtils.getValueBetween("<Publisher>", "</Publisher>", result);
            String developer = StringUtils.getValueBetween("<Developer>", "</Developer>", result);
            String rating = StringUtils.getValueBetween("<Rating>", "</Rating>", result);
            String urlBack = StringUtils.getValueBetween("<boxart side=\"back\"", "</boxart>", result);
            String urlFront = StringUtils.getValueBetween("<boxart side=\"front\"", "</boxart>", result);
            
            game.setValue(Software._B_DESCRIPTION, description);
            for (String genre : genres) {
                game.createReference(Software._K_CATEGORIES, genre);
            }
            
            if (!CoreUtilities.isEmpty(players)) {
                if (!players.equals("1"))
                    game.setValue(Software._AB_MULTI, Boolean.TRUE);
            }

            if (!CoreUtilities.isEmpty(coop)) {
                if (!coop.toLowerCase().equals("no")) {
                    game.setValue(Software._AA_COOP, Boolean.TRUE);
                    game.setValue(Software._AB_MULTI, Boolean.TRUE);
                }
            }
            
            if (!CoreUtilities.isEmpty(publisher))
                game.createReference(Software._G_PUBLISHER, publisher);

            if (!CoreUtilities.isEmpty(developer))
                game.createReference(Software._F_DEVELOPER, developer);
            
            if (!CoreUtilities.isEmpty(rating)) {
                try {
                    double d = Double.parseDouble(rating);
                    game.setValue(Software._E_RATING, Math.round(d));
                } catch (NumberFormatException e) {
                    logger.debug("Could not parse rating (" + rating + ") for " + game + " (TheGamesDB)", e);
                }
            }
            
            if (!CoreUtilities.isEmpty(urlBack)) {
                urlBack = urlBack.substring(urlBack.indexOf(">") + 1);
                urlBack = baseImgUrl + urlBack;
                game.setValue(Software._N_PICTUREBACK, new DcImageIcon(HttpConnectionUtil.retrieveBytes(urlBack)));
            }
            
            if (!CoreUtilities.isEmpty(urlFront)) {
                urlFront = urlFront.substring(urlFront.indexOf(">") + 1);
                urlFront = baseImgUrl + urlFront;
                game.setValue(Software._M_PICTUREFRONT, new DcImageIcon(HttpConnectionUtil.retrieveBytes(urlFront)));
            }
            
            String screenURL;
            int[] picFields = new int[] {Software._P_SCREENSHOTONE, Software._Q_SCREENSHOTTWO, Software._R_SCREENSHOTTHREE};
            int picIdx = 0;
            for (String screenshot : screenshots) {
                screenURL = StringUtils.getValueBetween("<original", "</", screenshot);
                screenURL = screenURL.substring(screenURL.indexOf(">") + 1);
                screenURL = baseImgUrl + screenURL;
                
                game.setValue(picFields[picIdx], new DcImageIcon(HttpConnectionUtil.retrieveBytes(screenURL)));
                
                picIdx++;
                
                if (picIdx >= 3) break;
            }
        } catch (Exception e) {
            logger.error(e, e);
        }
        
        return game;
    }

    @Override
    protected DcObject getItem(URL url) throws Exception {
        String key = url.toString();
        key = key.substring(key.lastIndexOf("/") + 1);
        return getItem(Integer.valueOf(key), true);
    }

    @Override
    protected Collection<Object> getItemKeys() throws Exception {
        Collection<Object> values = new ArrayList<Object>();
        String url = getAddress() + "GetGamesList.php?name=" + getQuery();
        
        String result = HtmlUtils.getHtmlCleaned(new URL(url), "UTF-8", 0);
        
        String id;
        String platform;
        String name;
        String date;
        for (String gameXml : StringUtils.getValuesBetween("<Game>", "</Game>", result)) {
            
            DcObject game = DcModules.get(DcModules._SOFTWARE).getItem();
            
            id = StringUtils.getValueBetween("<id>", "</id>", gameXml);
            name = StringUtils.getValueBetween("<GameTitle>", "</GameTitle>", gameXml);
            platform = StringUtils.getValueBetween("<Platform>", "</Platform>", gameXml);
            date = StringUtils.getValueBetween("<ReleaseDate>", "</ReleaseDate>", gameXml);
            
            game.setValue(Software._A_TITLE, name);
            game.createReference(Software._H_PLATFORM, platform);
            game.setValue(Software._C_YEAR, date.substring(date.lastIndexOf("/") + 1));
            game.setValue(Software._SYS_SERVICEURL, getAddress() + "GetGame.php?id=" + id);
            game.setValue(Software._I_WEBPAGE, "http://thegamesdb.net/game/" + id);
            game.addExternalReference(DcRepository.ExternalReferences._THEGAMESDB, id);
            
            setServiceInfo(game);
            
            values.add(game);
        }
        
        return values;
    }
}
