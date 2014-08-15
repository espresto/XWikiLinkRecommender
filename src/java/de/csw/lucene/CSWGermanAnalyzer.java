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

// This file is encoded in UTF-8

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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.de.GermanStemFilter;
import org.apache.lucene.analysis.miscellaneous.KeywordMarkerFilter;
import org.apache.lucene.analysis.pattern.PatternTokenizer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.Version;

import de.csw.ontology.OntologyIndex;
import de.csw.util.Config;

/**
 * Analyzer for German language. Supports an external list of stopwords (words that
 * will not be indexed at all) and an external list of exclusions (word that will
 * not be stemmed, but indexed).
 * A default set of stopwords is used unless an alternative list is specified, the
 * exclusion list is empty by default.
 *
 * 
 * @version $Id$
 */
public class CSWGermanAnalyzer extends Analyzer {

	/**
	 * List of typical german stopwords.
	 * deprecated fallback if snowball stopwords.txt are not found
	 */
	private final static String[] GERMAN_STOP_WORDS = {
		"einer", "eine", "eines", "einem", "einen", 
		"der", "die", "das", "dass", "daß",
		"du", "er", "sie", "es",
		"was", "wer", "wie", "wir",
		"und", "oder", "ohne", "mit",
		"am", "im", "in", "aus", "auf",
		"ist", "sein", "war", "wird",
		"ihr", "ihre", "ihres",
		"als", "für", "von", "mit",
		"dich", "dir", "mich", "mir",
		"mein", "sein", "kein",
		"durch", "wegen"
	};

	private static CharArraySet DEFAULT_STOPWORDS;

	static {
		try {
			DEFAULT_STOPWORDS = WordlistLoader.getSnowballWordSet(
					IOUtils.getDecodingReader(SnowballFilter.class, GermanAnalyzer.DEFAULT_STOPWORD_FILE, IOUtils.CHARSET_UTF_8), Version.LUCENE_40);
		} catch (IOException e) {
			DEFAULT_STOPWORDS = new CharArraySet(Version.LUCENE_40, Arrays.asList(GERMAN_STOP_WORDS), false);
		}
	}

	private CharArraySet stopwords;
	private CharArraySet stemExclusionSet;

	private final boolean withConcepts;

	/**
	 * Builds an analyzer with the default stop words
	 * (<code>GERMAN_STOP_WORDS</code>).
	 */
	public CSWGermanAnalyzer(boolean withConcepts) {
		this(withConcepts, DEFAULT_STOPWORDS);
	}

	/**
	 * Builds an analyzer with the given stop words.
	 * @param withConcepts if a conceptFilter should be attached. should be false e.g. while initializing the ontology index
	 * @param stopwords a list of stopwords; should not be null
	 */
	public CSWGermanAnalyzer(boolean withConcepts, CharArraySet stopwords) {
		this.stopwords = stopwords;
		this.withConcepts = withConcepts;
	}

	/**
	 * Builds a stem exclusion list from an array of Strings.
	 */
	public void setStemExclusionTable(String[] exclusionlist) {
		stemExclusionSet = StopFilter.makeStopSet(Version.LUCENE_40, exclusionlist);
	}

	/**
	 * Builds a stem exclusion list from the keys of a Map.
	 */
	public void setStemExclusionTable(Map exclusionlist) {
		stemExclusionSet = new CharArraySet(Version.LUCENE_40, exclusionlist.keySet(), false);
	}

	/**
	 * Builds an stem exclusion list from the words contained in the given file.
	 */
	public void setStemExclusionTable(File exclusionlist) throws IOException {
		stemExclusionSet = WordlistLoader.getWordSet(new FileReader(exclusionlist), Version.LUCENE_40);
	}

	@SuppressWarnings("resource")
	@Override
	protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
		Tokenizer source;

		try {
			source = new PatternTokenizer(reader, Pattern.compile("\\b"), -1);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		TokenStream result = source;
		result = new LowerCaseFilter(Version.LUCENE_40, result);
		result = new StopFilter(Version.LUCENE_40, result, stopwords);
		result = new WhitespaceAndPunctuationFilter(result);
		if (stemExclusionSet != null) {
			result = new KeywordMarkerFilter(result, stemExclusionSet);
		}
		result = attachStemFilter(result);
		
		if (withConcepts) {
			result = new ConceptFilter(result, OntologyIndex.get());
		}

		return new TokenStreamComponents(source, result);
	}

	/**
	 * attach the stemmer to the token stream.
	 * in a separate method for better overriding
	 * @param result the original token Stream
	 * @return the new tokenStream with the stemmer attached
	 */
	protected TokenStream attachStemFilter(TokenStream result) {
		if (Config.getBooleanAppProperty(Config.USE_LIGHT_STEMMER)) {
			result = new GermanNormalizationFilter(result);
			result = new GermanLightStemFilter(result);
			// if snowball should be used instead:
			// result = new SnowballFilter(result, "German");
		} else {
			result = new GermanStemFilter(result);
		}
		return result;
	}

}
