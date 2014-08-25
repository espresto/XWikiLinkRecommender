package de.csw.xwiki.plugin;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.script.service.ScriptService;

import de.csw.lucene.OntologyConceptAnalyzer;
import de.csw.util.PlainTextView;
import de.csw.util.URLEncoder;

@Component
@Named("termextraction")
public class TermExtractionAnalyzer implements ScriptService {

	@Inject
	private Logger logger;

	@Inject
	Provider<XWikiContext> contextProvider;

	@Inject
	@Named("plain/1.0")
	BlockRenderer plainTextRenderer;

	@Inject
	@Named("xwiki/2.1")
	BlockRenderer wikiTextRenderer;

	@Inject
	QueryManager qm;

	private Map<String, List<TermEntry>> spellingVariants;

	private List<TermEntry> tfidfResults;

	private List<TermEntry> simpleTermCountResults;

	private List<TermEntry> documentFrequencyResults, averageTermCountResults;

	private String spaceName;

	/**
	 * flag if alternatives now to extract the plain text from the wiki page.
	 * if true, use the "plain text renderer" (which expands macros, contains link labels etc).
	 * if false, use the PlainTextView, which removes macros and link labels from the text
	 */
	// FIXME: this should be a configuration variable
	private boolean plainTextRendering = false;

	// TODO: no language variants are exported here
	private Map<String, String> plainTextOfSpace(String space) throws QueryException, XWikiException {
		XWikiContext cx = contextProvider.get();

		Query q = qm.createQuery("where doc.space = :space", Query.XWQL);
		List<String> spaceDocs = q.bindValue("space", space).execute();

		Map<String, String> pageContent = new HashMap<>();
		logger.info("trying to start export");
		for (String docName : spaceDocs) {
			logger.info("with doc " + docName);
			XWikiDocument doc = cx.getWiki().getDocument(docName, cx);
			if (plainTextRendering) {
				XDOM xdom = doc.getXDOM();
				WikiPrinter printer = new DefaultWikiPrinter();
				plainTextRenderer.render(xdom, printer);
				pageContent.put(docName, printer.toString());
			} else {
				String unfiltered = doc.getContent();
				pageContent.put(docName, new PlainTextView(unfiltered).getPlainText());
			}
		}

		return pageContent;
	}

	/**
	 * UI class to describe a term with a "rank" (weight of some kind)
	 */
	public static final class TermEntry {
		final String term;
		final double rank;

		TermEntry(String term, double rank) {
			this.term = term;
			this.rank = rank;
		}

		public String getTerm() {
			return term;
		}

		public double getRank() {
			return rank;
		}
	}

	/**
	 * UI class for a term with a variant of spellings.
	 * the "term" attribute can be considered the unique key of this object
	 */
	public static final class TermDescription {
		final String term;
		final List<String> spellingVariants;

		TermDescription(String term, List<TermEntry> spellings) {
			if (spellings == null) {
				throw new IllegalArgumentException("no spellings given for term " + term);
			}
			this.term = term;
			List<String> _sp = new ArrayList<String>(spellings.size());
			for (TermEntry spelling : spellings) {
				_sp.add(spelling.term);
			}
			spellingVariants = Collections.unmodifiableList(_sp);
		}

		public String getTerm() {
			return term;
		}

		public List<String> getSpellingVariants() {
			return spellingVariants;
		}
	}

	private static final Comparator<TermEntry> SORT_BY_RANK = new Comparator<TermExtractionAnalyzer.TermEntry>() {
		@Override
		public int compare(TermEntry t1, TermEntry t2) {
			return - Double.compare(t1.rank, t2.rank);
		}
	};

	//
	// hackish: computation is synchronous with setting the space.
	// this is nowhere thread safe, of course
	// should use "xwiki-job" infrastructure instead or the like
	// 

	public void calculateTermsForSpace(String space) {
		try {
			doExtractTermsForSpace(space);
		} catch (QueryException | XWikiException e) {
			logger.error("could not extract pages for " + space, e);
		}
	}

	/** 
	 * if not null, then we should have a calculation of terms
	 * @return
	 */
	public String haveResultsFor() {
		return spaceName;
	}

	/**
	 * number of available terms from the last computation
	 * @return number of terms
	 */
	public int termCount() {
		return tfidfResults.size();
	}

	/**
	 * @param from offset from the beginning of the list. Start with zero.
	 * @params size (maximal) number of returned elements. If the list has less than <em>size</em> elements
	 *   after <em>from</em> a shorter (possible even an empty) list
	 * @return a sub list of the available terms, empty if wanted range not on the list of elements.
	 *   The list is not backed by the original terms list and can be modified without influencing
	 *   the original term list.
	 */
	public List<TermDescription> termsFor(int from, int size) {
		List<TermDescription> terms = new ArrayList<>(size);
		int toIndex = from + size;
		if (toIndex > tfidfResults.size()) {
			toIndex = tfidfResults.size();
		}
		if (from > toIndex || from < 0) { 
			from = toIndex;
		}
		for (TermEntry term : tfidfResults.subList(from, toIndex)) {
			terms.add(new TermDescription(term.term, spellingVariants.get(term.term)));
		}
		return terms;
	}

	// fill the instance variables with the results from a single space
	// FIXME: this should run in the background, and only once
	private void doExtractTermsForSpace(String space) throws QueryException, XWikiException {
		Map<String, String> pages = plainTextOfSpace(space);

		// different spelling variants per term
		Map<String, Map<String, Integer>> termSpellings = new HashMap<>();
		final int documentCount = pages.size();
		// count how often a giver term occurs
		Map<String, Integer> termFrequency = new HashMap<>();
		// count how many document contain a given term (no matter how often)
		Map<String, Integer> documentTermFrequency = new HashMap<>();

		Set<String> termsInCurrentDocument = new HashSet<>();
		int termCount = 0;
		for (String name : pages.keySet()) {
			String plainTextForName = pages.get(name);
			OntologyConceptAnalyzer tokenizer = new OntologyConceptAnalyzer(plainTextForName);
			while (tokenizer.hasNextMatch()) {
				if (tokenizer.isConcept()) {
					continue;
				}
				String term = tokenizer.token(); // we should only need this as a "unique id"
				String origTerm = plainTextForName.substring(tokenizer.startOfMatch(), tokenizer.endOfMatch());
				Map<String, Integer> spellings = termSpellings.get(term);
				if (spellings == null) {
					spellings = new HashMap<>();
					termSpellings.put(term, spellings);
				}
				inc(spellings, origTerm);

				termCount++;
				inc(termFrequency, term);
				if (!termsInCurrentDocument.contains(term)) {
					inc(documentTermFrequency, term);
					termsInCurrentDocument.add(term);
				}
			}
			tokenizer.close();
			termsInCurrentDocument.clear();
		}

		spellingVariants = new HashMap<>();
		for (Map.Entry<String, Map<String, Integer>> spellingCount : termSpellings.entrySet()) {
			List<TermEntry> spellingsForTerm = new ArrayList<>();
			for (Map.Entry<String, Integer> spellingVariant : spellingCount.getValue().entrySet()) {
				spellingsForTerm.add(new TermEntry(spellingVariant.getKey(), spellingVariant.getValue().doubleValue()));
			}
			Collections.sort(spellingsForTerm, SORT_BY_RANK);
			spellingVariants.put(spellingCount.getKey(), spellingsForTerm);
		}

		tfidfResults = new ArrayList<>();
		for (String term : termFrequency.keySet()) {
			double tf = ((double) termFrequency.get(term)) / ((double) termCount + 1);
			double idf = ((double) documentCount) / ((double) documentTermFrequency.get(term));
			tfidfResults.add(new TermEntry(term, tf * Math.log(idf)));
		}
		Collections.sort(tfidfResults, SORT_BY_RANK);

		simpleTermCountResults = new ArrayList<>();
		documentFrequencyResults = new ArrayList<>();
		for (String term : termFrequency.keySet()) {
			simpleTermCountResults.add(new TermEntry(term, (double) termFrequency.get(term)));
			documentFrequencyResults.add(new TermEntry(term, (double) documentTermFrequency.get(term)));
		}
		Collections.sort(simpleTermCountResults, SORT_BY_RANK);
		Collections.sort(documentFrequencyResults, SORT_BY_RANK);

		averageTermCountResults = new ArrayList<>();
		for (String term : termFrequency.keySet()) {
			averageTermCountResults.add(new TermEntry(term, (double) termFrequency.get(term) / ((double) documentTermFrequency.get(term))));
		}
		Collections.sort(averageTermCountResults, SORT_BY_RANK);

		spaceName = space;
	}
	
	// helper to increase counter, null safe
	private static final void inc(Map<String, Integer> counterMap, String key) {
		Integer value = counterMap.get(key);
		if (value == null) {
			value = 1;
		} else {
			value++;
		}
		counterMap.put(key, value);
	}

	//
	// debugging and experimental code -to be scrapped
	//

	/**
	 * dumps out plain text view to a directory on the local file server - for debugging only
	 */
	@Deprecated
	public void dumpPlainText(String rootDir, String... spaces) throws QueryException, IOException, XWikiException {
		File exportRoot = writeableDirectory(rootDir);

		for (String space : spaces) {
			Map<String, String> pages = plainTextOfSpace(space);
			for (String name : pages.keySet()) {
				String fileName = URLEncoder.encode(name) + ".txt";
				File ptextFile = new File(exportRoot, fileName);
				logger.info("... writing to file " + ptextFile);
				OutputStream output = new BufferedOutputStream(new FileOutputStream(ptextFile));
				IOUtils.write(pages.get(name), output, "UTF-8");
				output.close();
			}
		}
	}

	
	/**
	 * helper to dump results on the local file system - for debugging only
	 * @param rootDir
	 */
	@Deprecated
	public void dumpVocabulary(String rootDir, String... spaces) throws QueryException, IOException, XWikiException {
		File exportRoot = writeableDirectory(rootDir);

		for (String space : spaces) {
			doExtractTermsForSpace(space);
			dumpRankToFile(simpleTermCountResults, new File(exportRoot, "simple-tf-" + space + ".txt"));
			dumpRankToFile(documentFrequencyResults, new File(exportRoot, "simple-df-" + space + ".txt"));
			dumpRankToFile(tfidfResults, new File(exportRoot, "tfidf-" + space + ".txt"));
			dumpRankToFile(averageTermCountResults, new File(exportRoot, "avg-tf-" + space + ".txt"));

		}
	}


	protected void dumpRankToFile(List<TermEntry> entries, File dumpFile) throws IOException {
		PrintWriter pw = new PrintWriter(dumpFile, "UTF-8");
		for (TermEntry entry : entries) {
			List<TermEntry> termSpell = spellingVariants.get(entry.term);
			pw.print(entry.term);
			for (TermEntry spelling : termSpell) {
				pw.print(" | ");
				pw.print(spelling.term);
			}
			pw.println(String.format("\t\t%f", entry.rank));
		}
		pw.close();
	}
	private File writeableDirectory(String path) throws IOException {
		File exportRoot = new File(path);
		exportRoot.mkdirs();
		if (!exportRoot.canWrite()) {
			throw new IOException("cannot write into " + path);
		}
		return exportRoot;
	}
}
