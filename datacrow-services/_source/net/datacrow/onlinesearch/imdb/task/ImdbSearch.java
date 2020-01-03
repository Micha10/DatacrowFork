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

import net.datacrow.core.services.IOnlineSearchClient;
import net.datacrow.core.services.Region;
import net.datacrow.core.services.SearchMode;
import net.datacrow.core.services.SearchTask;
import net.datacrow.core.services.plugin.IServer;

public abstract class ImdbSearch extends SearchTask {

    protected final static int _RUNTIME = 0;
    protected final static int _USERRATING = 1;
    protected final static int _COUNTRY = 2;
    protected final static int _CERTIFICATION = 3;
    protected final static int _GENRE = 4;
    protected final static int _TRIVIA = 5;
    protected final static int _LANGUAGE = 6;
    protected final static int _COLOR = 7;
    protected final static int _ASPECT_RATIO = 8;
    protected final static int _MORE = 9;
    protected final static int _DIRECTOR = 10;
    protected final static int _DIRECTORS = 11;
    
    private String[] tag;  
    
    public ImdbSearch(IOnlineSearchClient listener, 
                      IServer server, 
                      Region region, 
                      SearchMode mode,
                      String query) {
        
        super(listener, server, region, mode, query);
        
        if (region.getCode().equals("us") || region.getCode().equals("default"))
            tag = new String[] {"Runtime:", "User Rating:", "Country:", "Certification:", "Genres:", "Trivia:", "Language:", "Color:", "Aspect Ratio:", "See more", "Director:", "Directors:"};
        else if (region.getCode().equals("it"))
            tag = new String[] {"Durata:", "Voti degli utenti:", "Nazionalità:", "Divieti:", "Genere:", "Trivia:", "Lingua:", "Colore:", "Aspect Ratio:", "Vedi altro", "Regista:", "Registi:"};
        else if (region.getCode().equals("de"))
            tag = new String[] {"Länge:", "Nutzer-Bewertung:", "Land:", "Altersfreigabe:", "Genre:", "Trivia:", "Sprache:", "Farbe:", "Seitenverhältnis:", "Mehr ansehen", "Regisseur:", "Regie:"};
        else if (region.getCode().equals("fr"))
            tag = new String[] {"Durée:", "Note des utilisateurs", "Pays:", "Classification:", "Genre:", "Trivia:", "Langue:", "Couleur:", "Rapport de forme:", "Voir plus", "Réalisateur:", "Réalisateurs:"};
        else if (region.getCode().equals("sp"))
            tag = new String[] {"Duración:", "Calificación:", "País:", "Clasificación:", "Género:", "Trivia:", "Idioma:", "Color:", "Relación de Aspecto:", "Ver más", "Director:", "Directores:"};
    }
    
    protected String getTag(int idx) {
        return tag[idx];
    }
    
    @Override
    public String getWhiteSpaceSubst() {
        return "%20";
    }
}
