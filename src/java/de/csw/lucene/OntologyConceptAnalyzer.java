package de.csw.lucene;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import com.hp.hpl.jena.ontology.OntResource;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * facade wrapping the lucene analyzer to loop over a text and extract the concepts.
 * 
 * instances of this class are not thread safe!
 */
// ToDo: use a "tokenizer/analyzer" xwiki-component or the like instead
public class OntologyConceptAnalyzer implements Closeable {

	static final Logger log = Logger.getLogger(OntologyConceptAnalyzer.class);

	private TokenStream ts;

	private CharTermAttribute charTermAttribute;
	private OffsetAttribute offsetAttribute;
	private ConceptAttribute conceptAttribute;
	private CSWGermanAnalyzer ga;

	private boolean exceptionCaught;

	/**
	 * for a given text return a list of (lucene analyzed) tokens.
	 * This is a small helper to convert e.g. resource labels into token lists
	 * that makes them recognizable in a token stream of a longer text.
	 * The tokens here went through the same analysis as any text feed into
	 * the OntologyConceptAnalyzer, except for concept detection.
	 * 
	 * @param inputText the text of break into tokens, should not be null
	 * @return the list of (stemmed) tokens; null on error
	 */
	public static List<String> tokenize(String inputText) {
		if (inputText != null) {
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
		}
		return null;

	}

	public OntologyConceptAnalyzer(String textToAnalyze) {
		this(textToAnalyze, true);
	}

	private OntologyConceptAnalyzer(String textToAnalyze, boolean withConcepts) {
		Reader r = new StringReader(textToAnalyze);

		ga = new CSWGermanAnalyzer(withConcepts);
		try {
			ts = ga.tokenStream("", r);
			ts.reset();

			charTermAttribute = ts.addAttribute(CharTermAttribute.class);
			offsetAttribute = ts.addAttribute(OffsetAttribute.class);
			if (withConcepts) {
				conceptAttribute = ts.addAttribute(ConceptAttribute.class);
			}
		} catch (IOException ioe) {
			exceptionCaught = true;
			ga.close();
			log.warn("init failed in " + textToAnalyze);
		}

	}

	public boolean hasNextMatch() {
		if (exceptionCaught) {
			return false;
		}
		try {
			final boolean hasNextToken = ts.incrementToken();
			if (!hasNextToken) {
				ts.end();
			}
			return hasNextToken;
		} catch (IOException ioe) {
			log.warn("hasNextToken failed", ioe);
			exceptionCaught = true;
			return false;
		}
	}

	public int startOfMatch() {
		return offsetAttribute.startOffset();
	}

	public int endOfMatch() {
		return offsetAttribute.endOffset();
	}

	public boolean isConcept() {
		return conceptAttribute.isConcept();
	}

	// the matching token, in *transformed* form
	// only used if no concept filter
	String token() {
		return String.copyValueOf(charTermAttribute.buffer(), 0, charTermAttribute.length());
	}

	public List<OntResource> concepts() {
		return conceptAttribute.getConcepts();
	}

	@Override
	public void close() {
		ga.close();
	}
}
