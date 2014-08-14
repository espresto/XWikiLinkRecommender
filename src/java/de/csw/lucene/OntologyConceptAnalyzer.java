package de.csw.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.de.CSWGermanAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/**
 * facade wrapping the lucene analyzer to loop over a text and extract the concepts,
 * in a manner similar to regexPattern.appendReplacement.appentTail.  
 * instances of this class are not thread safe!
 */
public class OntologyConceptAnalyzer implements Closeable {

	private final TokenStream ts;

	private final CharTermAttribute charTermAttribute;
	private final OffsetAttribute offsetAttribute;
	private final TypeAttribute typeAttribute;

	private CSWGermanAnalyzer ga;

	public static List<String> tokenize(String inputText) {
		try {
			List<String> results = new ArrayList<String>();
			OntologyConceptAnalyzer ce = new OntologyConceptAnalyzer(inputText, false);
			try {
				while (ce.hasNextMatch()) {
					results.add(ce.token());
				}
			} finally {
				ce.close();
			}

			return results;
		} catch (IOException ioe) {
			// this should not happen
		}
		return null;

	}

	public OntologyConceptAnalyzer(String textToAnalyze) throws IOException {
		this(textToAnalyze, true);
	}

	public OntologyConceptAnalyzer(String textToAnalyze, boolean withConcepts) throws IOException {
		Reader r = new StringReader(textToAnalyze);

		ga = new CSWGermanAnalyzer(withConcepts);
		try {
			ts = ga.tokenStream("", r);

			charTermAttribute = ts.addAttribute(CharTermAttribute.class);
			offsetAttribute = ts.addAttribute(OffsetAttribute.class);
			typeAttribute = ts.addAttribute(TypeAttribute.class);
		} catch (IOException ioe) {
			ga.close();
			throw ioe;
		}

	}

	public boolean hasNextMatch() throws IOException {
		final boolean hasNextToken = ts.incrementToken();
		if (!hasNextToken) {
			ts.end();
		}
		return hasNextToken;
	}

	public int startOfMatch() {
		return offsetAttribute.startOffset();

	}

	public int endOfMatch() {
		return offsetAttribute.endOffset();
	}

	public boolean isConcept() {
		return typeAttribute.type().equals(ConceptFilter.CONCEPT_TYPE);
	}

	// the matching token, in *transformed* form
	public String token() {
		return String.copyValueOf(charTermAttribute.buffer(), 0, charTermAttribute.length());
	}

	@Override
	public void close() throws IOException {
		ga.close();
	}
}
