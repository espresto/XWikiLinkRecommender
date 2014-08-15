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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hp.hpl.jena.ontology.OntResource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import de.csw.lucene.OntologyConceptAnalyzer;
import de.csw.ontology.util.OntologyUtils;
import de.csw.util.Config;
import de.csw.util.PlainTextView;
import de.csw.util.URLEncoder;

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
		StringBuilder result = new StringBuilder();
		PlainTextView plainTextView = new PlainTextView(text);
		try {

			OntologyConceptAnalyzer oca = new OntologyConceptAnalyzer(plainTextView.getPlainText());
			try {
				int lastEndIndex = 0;
				while (oca.hasNextMatch()) {

					final int startTextOffset = plainTextView.getOriginalPosition(oca.startOfMatch());
					final int endTextOffset = plainTextView.getOriginalEndPosition(oca.endOfMatch());

					result.append(text.substring(lastEndIndex, startTextOffset));
					final List<OntResource> concepts = oca.concepts();
					if (log.isDebugEnabled()) {
						log.debug("****EM: XWikiTextEnhancer.enhance2, concept: " + concepts + "\nstartOffset(): " + startTextOffset + "\nendOffset(): "
								+ endTextOffset + "\norigText: " + text.substring(startTextOffset, endTextOffset));
					}

					if (oca.isConcept()) {
						log.debug("Annotating concept: " + concepts);
						annotateWithSearch(result, text.substring(startTextOffset, endTextOffset), concepts);
					} else {
						result.append(text.substring(startTextOffset, endTextOffset));
					}

					lastEndIndex = endTextOffset;
				}
				result.append(text.subSequence(lastEndIndex, text.length()));
			} finally {
				oca.close();
			}

		} catch (Exception e) {
			log.error("Error while processing the page content", e);
		}
		log.debug("****EM: XWikiTextEnhancer.enhance4, result: " + result.toString());

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
	 * @param concepts 
	 *            the concepts found for the term
	 */
	protected void annotateWithSearch(StringBuilder sb, String term, List<OntResource> concepts) {

		List<String> originalTerms = OntologyUtils.getLabels(concepts);
		List<OntResource> similarConcepts = index.getSimilarMatches(concepts, MAX_SIMILAR_CONCEPTS);
		List<String> matches = OntologyUtils.getLabels(similarConcepts);

		if (matches.isEmpty()) {
			sb.append(term);
			return;
		}

		boolean filterFirst = !originalTerms.containsAll(matches);
		sb.append("[[").append(term);
		sb.append(">>").append(getSearchURL(matches));
		sb.append("||class=\"similarconcept\"");
		sb.append(" title=\"Suche nach den verwandten Begriffen: ");
		boolean afterFirstTerm = false;
		
		for (String similarTerm : matches) {
			if (!filterFirst || !originalTerms.contains(similarTerm)) {
				if (afterFirstTerm) {
					sb.append(", ");
				}
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
		List<String> phrasesQuoted = new ArrayList<>(terms.size());
		for (String term : terms) {
			if (term.indexOf(' ') != -1) {
				term = '"' + term + '"';
			}
			phrasesQuoted.add(term);
		}
		return LUCENE_URL + "?text=" + URLEncoder.encode(StringUtils.join(phrasesQuoted, ' '));
	}
}
