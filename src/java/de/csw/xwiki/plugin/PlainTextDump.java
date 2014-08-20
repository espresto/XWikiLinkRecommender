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
import de.csw.ontology.XWikiTextEnhancer;
import de.csw.util.PlainTextView;
import de.csw.util.URLEncoder;

@Component
@Named("plaintextdump")
public class PlainTextDump implements ScriptService {

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

	private boolean plainTextRendering = false;

	private Map<String, String> plainTextOfSpace(String space) throws QueryException, XWikiException {
		XWikiContext cx = contextProvider.get();

		XWikiTextEnhancer textEnhancer = new XWikiTextEnhancer();

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
				//String enhanced = textEnhancer.enhance(unfiltered);
				//pageContent.put(docName, enhanced);
				pageContent.put(docName, new PlainTextView(unfiltered).getPlainText());
			}
		}

		return pageContent;
	}

	// hack for CSC-91: a helper to count all words
	// dumps out plain text view and let an extra utility run the thing
	public void dumpPlainText(String rootDir) throws QueryException, IOException, XWikiException {
		File exportRoot = writeableDirectory(rootDir);

		String[] spaces = { "saas" /*, "Sandbox" */};
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

	static final class TermEntry {
		final String term;
		final double rank;

		TermEntry(String term, double rank) {
			this.term = term;
			this.rank = rank;
		}
	}

	static final Comparator<TermEntry> SORT_BY_RANK = new Comparator<PlainTextDump.TermEntry>() {
		@Override
		public int compare(TermEntry t1, TermEntry t2) {
			return -Double.compare(t1.rank, t2.rank);
		}
	};

	public void dumpVocabulary(String rootDir) throws QueryException, IOException, XWikiException {
		File exportRoot = writeableDirectory(rootDir);

		String[] spaces = { "saas" /*, "Sandbox" */};
		for (String space : spaces) {
			Map<String, String> pages = plainTextOfSpace(space);

			// different spelling variants per term
			Map<String, Set<String>> termSpellings = new HashMap<>();
			final int documentCount = pages.size();
			// count how often a giver term occurs
			Map<String, Integer> termFrequency = new HashMap<>();
			// count how many document contain a given term (no matter how often)
			Map<String, Integer> documentTermFrequency = new HashMap<>();

			Set<String> termsInCurrentDocument = new HashSet<>();
			int termCount = 0;
			for (String name : pages.keySet()) {
				String plainTextForName = pages.get(name);
				// here it would be nice to keep the original terms; use a different tool
				OntologyConceptAnalyzer tokenizer = new OntologyConceptAnalyzer(plainTextForName, false);
				while (tokenizer.hasNextMatch()) {
					String term = tokenizer.token();
					String origTerm = plainTextForName.substring(tokenizer.startOfMatch(), tokenizer.endOfMatch());
					Set<String> spellings = termSpellings.get(term);
					if (spellings == null) {
						spellings = new HashSet<>();
						termSpellings.put(term, spellings);
					}
					spellings.add(origTerm);

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

			List<TermEntry> tfidf = new ArrayList<>();
			for (String term : termFrequency.keySet()) {
				double tf = ((double) termFrequency.get(term)) / ((double) termCount + 1);
				double idf = ((double) documentCount) / ((double) documentTermFrequency.get(term));
				tfidf.add(new TermEntry(term, tf * Math.log(idf)));
			}

			dumpRankToFile(tfidf, new File(exportRoot, "tfidf-" + space + ".txt"), termSpellings);

			List<TermEntry> termFreq = new ArrayList<>();
			List<TermEntry> documentFreq = new ArrayList<>();
			for (String term : termFrequency.keySet()) {
				termFreq.add(new TermEntry(term, (double) termFrequency.get(term)));
				documentFreq.add(new TermEntry(term, (double) documentTermFrequency.get(term)));
			}

			dumpRankToFile(termFreq, new File(exportRoot, "simple-tf-" + space + ".txt"), termSpellings);
			dumpRankToFile(documentFreq, new File(exportRoot, "simple-df-" + space + ".txt"), termSpellings);

			List<TermEntry> avgTermFreq = new ArrayList<>();
			for (String term : termFrequency.keySet()) {
				avgTermFreq.add(new TermEntry(term, (double) termFrequency.get(term) / ((double) documentTermFrequency.get(term))));
			}

			dumpRankToFile(avgTermFreq, new File(exportRoot, "avg-tf-" + space + ".txt"), termSpellings);

		}
	}

	protected void dumpRankToFile(List<TermEntry> entries, File dumpFile, Map<String, Set<String>> spellings) throws IOException {
		Collections.sort(entries, SORT_BY_RANK);
		PrintWriter pw = new PrintWriter(dumpFile, "UTF-8");
		for (TermEntry entry : entries) {
			List<String> termSpell = new ArrayList<String>(spellings.get(entry.term));
			Collections.sort(termSpell);
			pw.print(entry.term);
			for (String spelling : termSpell) {
				pw.print(" | ");
				pw.print(spelling);
			}
			pw.println(String.format("\t\t%f", entry.rank));
		}
		pw.close();
	}

	private static final void inc(Map<String, Integer> counterMap, String key) {
		Integer value = counterMap.get(key);
		if (value == null) {
			value = 1;
		} else {
			value++;
		}
		counterMap.put(key, value);
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
