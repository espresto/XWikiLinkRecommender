/*******************************************************************************
 * This file is part of the Coporate Semantic Web Project.
 * 
 * This work has been partially supported by the ``InnoProfile-Corporate Semantic Web" project funded by the German Federal
 * Ministry of Education and Research (BMBF) and the BMBF Innovation Initiative for the New German Laender - Entrepreneurial Regions.
 * 
 * http://www.corporate-semantic-web.de/
 * 
 * Freie Universitaet Berlin
 * Copyright (c) 2007-2013
 * 
 * Institut fuer Informatik
 * Working Group Coporate Semantic Web
 * Koenigin-Luise-Strasse 24-26
 * 14195 Berlin
 * 
 * http://www.mi.fu-berlin.de/en/inf/groups/ag-csw/
 * 
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA or see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package de.csw.ontology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.de.CSWGermanAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.jfree.util.Log;

import de.csw.lucene.ConceptFilter;
import de.csw.util.Config;
import de.csw.util.URLEncoder;
import de.csw.xwiki.txtservice.util.PlainTextView;

/**
 * Uses background knowledge to enhance the text.
 * 
 * @author rheese
 * 
 */
public class XWikiTextEnhancer implements TextEnhancer {
	static final Logger log = Logger.getLogger(XWikiTextEnhancer.class);
	
	static final int MAX_SIMILAR_CONCEPTS = Config.getIntAppProperty(Config.LUCENE_MAXSEARCHTERMS);
	static final String LUCENE_URL = Config.getAppProperty(Config.LUCENE_URL);
	
	OntologyIndex index;
	
	public XWikiTextEnhancer() {
		index = OntologyIndex.get();
	}

	/**
	 * The enhanced text contains links to the Lucene search page of the xWiki
	 * system. The search terms are related to the annotated phrase.
	 */
	public String enhance(String text) {
		CSWGermanAnalyzer ga = new CSWGermanAnalyzer();
		TokenStream ts = null;
		StringBuilder result = new StringBuilder();
		
		PlainTextView plainTextView = new PlainTextView(text);
		
		try {
			Reader r = new BufferedReader(new StringReader(plainTextView.getPlainText()));
			
			ts = ga.tokenStream("",	 r);
			
			CharTermAttribute charTermAttribute = ts.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttribute = ts.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = ts.addAttribute(TypeAttribute.class);
			
			String term;
			int lastEndIndex = 0;
			
			while(ts.incrementToken()) {
			
				final int endOtherOffset = plainTextView.getOriginalEndPosition(offsetAttribute.startOffset());
				final int startTextOffset = plainTextView.getOriginalPosition(offsetAttribute.startOffset());
				final int endTextOffset = plainTextView.getOriginalEndPosition(offsetAttribute.endOffset());
				
				result.append(text.substring(lastEndIndex, endOtherOffset));
				term = String.copyValueOf(charTermAttribute.buffer(), 0, charTermAttribute.length());
				if (log.isDebugEnabled()) {
				    log.debug("****EM: XWikiTextEnhancer.enhance2, concept: "+ term + "\nstartOffset(): "+ endOtherOffset 
				            + "\nendOffset(): "+offsetAttribute.endOffset()+"\ntoken.termBuffer(): "+ new String(charTermAttribute.buffer()) + "\nToken.term: "+charTermAttribute+ "\nToken.type: "+typeAttribute.type());
				}
				
				if (typeAttribute.type().equals(ConceptFilter.CONCEPT_TYPE)) {
					log.debug("Annotating concept: " + term);
					annotateWithSearch(result, text.substring(startTextOffset, endTextOffset), term);
				} else {
					result.append(text.substring(startTextOffset, endTextOffset));
				}
					
				lastEndIndex = plainTextView.getOriginalPosition(offsetAttribute.endOffset());
			}
			result.append(text.subSequence(lastEndIndex, text.length()));
		} catch (IOException e) {
			Log.error("Error while processing the page content", e);
		}
		log.debug("****EM: XWikiTextEnhancer.enhance4, result: "+ result.toString());		
		
		ga.close();
		return result.toString();
	}
	


	/**
	 * Annotates the term by linking <code>term</code> to the search page of the
	 * wiki.
	 * 
	 * @param sb 
	 *            the string builder the result is appended to
	 * @param term
	 *            a term
	 * @param stemBase 
	 *            the base form of the term
	 */
	protected void annotateWithSearch(StringBuilder sb, String term, String stemBase) {
		List<String> matches = index.getSimilarMatchLabels(term, MAX_SIMILAR_CONCEPTS);

		if (matches.isEmpty()) {
			sb.append(term);
			return;
		}

		sb.append("[[").append(term);
		sb.append(">>").append(getSearchURL(matches));
		sb.append("||class=\"similarconcept\"");
		Iterator<String> it = matches.listIterator();
		sb.append(" title=\"Suche nach den verwandten Begriffen: ");
		boolean afterFirstTerm = false;
		while (it.hasNext()) {
			String similarTerm = it.next();
			if (!stemBase.equals(this.index.getStemmer().stem(similarTerm))) {
				if (afterFirstTerm) { sb.append(", "); }
				sb.append(similarTerm);
				afterFirstTerm = true;
			}
		}
		sb.append("\"]]");
	}
	
	/**
	 * Creates a link to the search wiki page.
	 * 
	 * @param terms
	 *            a collection of search terms
	 * @return the link
	 */
	protected String getSearchURL(Collection<String> terms) {
		log.debug("** search terms: " + terms);
		return LUCENE_URL + "?text=" + URLEncoder.encode(StringUtils.join(terms, ' '));
	}
}
