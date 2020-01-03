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

package net.datacrow.onlinesearch.isbndb.server;

import java.util.ArrayList;
import java.util.Collection;

import net.datacrow.core.modules.DcModules;
import net.datacrow.core.objects.DcObject;
import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.Region;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.plugin.IServer;
import net.datacrow.core.settings.Setting;
import net.datacrow.onlinesearch.isbndb.task.ISBNdbBookSearch;

public class ISBNdbServer implements IServer {
    
    private static final long serialVersionUID = 2493036885013017344L;

    private Collection<Region> regions = new ArrayList<Region>();
    private Collection<SearchMode> modes = new ArrayList<SearchMode>();

    public ISBNdbServer() {
        regions.add(new Region("us", "Default (english)", "http://isbndb.com/"));
    }

    @Override
    public int getModule() {
        return DcModules._BOOK;
    }
    
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Collection<Setting> getSettings() {
        return null;
    }

    @Override
    public String getName() {
        return "ISBNdb.com";
    }
    
    @Override
    public boolean isFullModeOnly() {
        return false;
    }
    
    @Override
    public Collection<Region> getRegions() {
        return regions;
    }

    @Override
    public Collection<SearchMode> getSearchModes() {
        return modes;
    }

    @Override
    public String getUrl() {
        return "http://isbndb.com";
    }
    
    @Override
    public SearchTask getSearchTask(IOnlineSearchClient listener, SearchMode mode, Region region, String query, DcObject client) {
        ISBNdbBookSearch task = new ISBNdbBookSearch(listener, this, mode, query);
        task.setClient(client);
        return task;
    }
    
    @Override
    public String toString() {
        return getName();
    }
}
