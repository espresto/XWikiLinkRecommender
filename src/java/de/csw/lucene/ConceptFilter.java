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
package de.csw.lucene;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.AttributeSource;

import de.csw.ontology.OntologyIndex;

/**
 * A filter that detects concepts from an ontology in the token stream. A
 * concept attribute is filled with he concepts found.
 * 
 * @author rheese
 * 
 */
public final class ConceptFilter extends TokenFilter {
	private static final Logger log = Logger.getLogger(ConceptFilter.class);

	private final OntologyIndex index;

	/** Tokens that have been read ahead */
	private final Queue<AttributeSource.State> queue = new LinkedList<AttributeSource.State>();

	/** the attributes of the token which the filter is currently reading */
	private final CharTermAttribute charTermAttribute = addAttribute(CharTermAttribute.class);
	private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
	private final AfterPunctuationAttribute punctAttribute = addAttribute(AfterPunctuationAttribute.class);
	private final ConceptAttribute conceptAttribute = addAttribute(ConceptAttribute.class);

	/**
	 * Build a ConceptFilter that uses a given ontology index
	 * 
	 * @param in
	 *            a token stream
	 * @param oi
	 *            a ontology index
	 */
	public ConceptFilter(TokenStream in, OntologyIndex oi) {
		super(in);
		index = oi;
	}

	/**
	 * advances to the next token in the stream.
	 * Takes into account that terms from the ontology might be constructed
	 * out of several consecutive tokens.
	 * @return false at EOS
	 */
	@Override
	public boolean incrementToken() throws IOException {

		boolean hasMoreToken = innerNextToken();
		if (!hasMoreToken) {
			return false;
		}

		Queue<AttributeSource.State> lookAhead = new LinkedList<AttributeSource.State>();
		List<String> terms = new ArrayList<String>();
		terms.add(String.copyValueOf(charTermAttribute.buffer(), 0, charTermAttribute.length()));

		boolean noPunctuationInterrupt = true;
		while (index.isPrefix(terms) && hasMoreToken && noPunctuationInterrupt) {
			lookAhead.add(captureState());
			hasMoreToken = innerNextToken();
			if (hasMoreToken) {
				terms.add(String.copyValueOf(charTermAttribute.buffer(), 0, charTermAttribute.length()));
				noPunctuationInterrupt = !punctAttribute.isAfterPunct();
			}
		}
		// TODO: if we have matches for "A" and "A B", but see "A, B", this does not find "A" (yet)

		
		// if we have a match ...
		if (noPunctuationInterrupt && index.hasExactMatches(terms)) {

			// ... then we consume all elements in the look ahead, if present
			if (!lookAhead.isEmpty()) {
				int maxEndOffset = offsetAttribute.endOffset();
				restoreState(lookAhead.poll());
				// first term already present in current token; append the rest
				// XXX do we really need to do this here? we have all the information in the tokens
				//for (String term : terms.subList(1, terms.size())) {
				//	charTermAttribute.append(OntologyIndex.PREFIX_SEPARATOR);
				//	charTermAttribute.append(term);
				//}

				offsetAttribute.setOffset(offsetAttribute.startOffset(), maxEndOffset);
			}

			// important: only set attributes after we have finished messing with "restoreState"
			conceptAttribute.setConcepts(index.getExactMatches(terms));
			if (log.isTraceEnabled()) {
				log.trace("Concept token recognized: " + terms);
			}

		} else {
			conceptAttribute.setConcepts(null);
			// .. else we push back in the queue the tokens already read
			if (!lookAhead.isEmpty()) {
				lookAhead.add(captureState());
				restoreState(lookAhead.poll());
				for (AttributeSource.State laterToken : lookAhead) {
					queue.add(laterToken);
				}
			}
		}

		return hasMoreToken;
	}

	private boolean innerNextToken() throws IOException {
		if (!queue.isEmpty()) {
			restoreState(queue.poll());
			return true;
		}
		return input.incrementToken();
	}

	public void reset() throws IOException {
		super.reset();
		queue.clear();
	}

}
