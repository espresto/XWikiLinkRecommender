/**
 * 
 */
package de.csw.linkgenerator.struts;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.XWikiAction;
import com.xpn.xwiki.web.XWikiRequest;

import de.csw.linkgenerator.plugin.lucene.LucenePluginApi;
import de.csw.linkgenerator.plugin.lucene.SearchResult;
import de.csw.linkgenerator.plugin.lucene.SearchResults;
import de.csw.ontology.OntologyIndex;

import de.csw.util.URLEncoder;

/**
 * @author ralph
 *
 */
public class CSWLinkAction extends XWikiAction {

    private static final Log LOG = LogFactory.getLog(CSWLinkAction.class);
	
	@Override
	public boolean action(XWikiContext context) throws XWikiException {
		
		XWikiRequest request = context.getRequest();
		String query = request.get("text");
		if (query == null) {
			return false;
		}
		
		PrintWriter out = null;
		try {
			out = context.getResponse().getWriter();
			context.getResponse().setContentType("text/plain");
			
		} catch (IOException e) {
			return false;
		}

		LucenePluginApi lucene = (LucenePluginApi)context.getWiki().getPluginApi("csw.linkgenerator.lucene", context);
LOG.debug("***EM: CWSLinkAction.action *********************************");
		SearchResults searchResults = lucene.getSearchResults(query, "de, en");

LOG.debug("***EM2: CWSLinkAction.action, query: "+query+", Anzahl Results: "+ searchResults.getHitcount() + "  **************");
		
		Iterator<SearchResult> results = searchResults.getResults(1, 10).iterator();
		
		// optimized (only one hasNext() check per iteration)
		if (results.hasNext()) {
			for (;;) {
				SearchResult searchResult = results.next();
LOG.debug("***EM: CWSLinkAction.action searchResult.Space/Name, score: "+ searchResult.getSpace()+"/"+searchResult.getName()+", score: "+ searchResult.getScore());				
				out.write(URLEncoder.encode(searchResult.getSpace()));
				out.write('/');
				out.write(URLEncoder.encode(searchResult.getName()));
				if (results.hasNext()) {
					out.write('\n');
				} else {
					break;
				}
			}
		}
		
		return true;
	}
}
