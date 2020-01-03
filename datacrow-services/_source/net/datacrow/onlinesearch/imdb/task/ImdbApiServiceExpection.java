package net.datacrow.onlinesearch.imdb.task;

public class ImdbApiServiceExpection extends Exception {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = -921259750747833001L;

	public ImdbApiServiceExpection(Exception e, String msg) {
        super(msg, e);
    }
}
