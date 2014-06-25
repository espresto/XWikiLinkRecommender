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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.de.GermanStemmer;
import org.apache.lucene.analysis.de.Stemmer;

import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

import de.csw.ontology.util.OntologyUtils;
import de.csw.util.Config;

/**
 * The class encapsulates the access to an ontology.
 * 
 * @author rheese
 * 
 */
public class OntologyIndex {
	static final Logger log = Logger.getLogger(OntologyIndex.class);
	
	/** character that is used to concatenate two fragments in the context of a prefix index */
	public static final char PREFIX_SEPARATOR = ' ';
	
	static OntologyIndex instance;
	
	/** Stemmer to get discriminators from a term. By default it is a GermanStemmer */
	Stemmer stemmer;

	/** Index for mapping labels (in its stemmed version) to concepts of the ontology. */
	// TODO encapsulate in a separate class
	Map<String, OntClass[]> labelIdx = new HashMap<String, OntClass[]>();
	
	/** The set contains all prefixes of the concepts, see {@link #generatePrefixes(String)} */
	// TODO encapsulate in a separate class
	Set<String> prefixIdx = new HashSet<String>(); 
	
	/** Jena model of the ontology */
	OntModel model;
	
	/**
	 * Use {@link #get()} to retrieve an instance. The constructor creates an
	 * instance containing an empty ontology model.
	 */

	// EM: for check the namespace	
	//public final String nameSpace_qm ="http://ontologie.datenlabor-berlin.de/CSC/"; 
	//public final String nameSpace_qmt ="http://ontologie.datenlabor-berlin.de/CSC/2014/3/qmt";

	private OntologyIndex() {
//		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM);
//		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);	       funktioniert, equivalentClass nur in eine Richtung
//		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);	//RDF RuleReasoner
//		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_TRANS_INF);	//RDF RuleReasoner
//		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RULE_INF);	//OWL RuleReasoner - symmetric equivalentClass
//		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RDFS_INF);	//OWL RuleReasoner - keine equivalentClass
		model = ModelFactory.createOntologyModel(OntModelSpec.OWL_LITE_MEM_RULES_INF);	//OWL Lite RuleReasoner - symmetric equivalentClass
		stemmer = new GermanStemmer();
	}

	/**
	 * @return the only OntologyIndex instance.
	 */
	public static OntologyIndex get() {
		if (instance == null)
			instance = new OntologyIndex();
		return instance;
	}

	/**
	 * @return the ontology model
	 */
	public OntModel getModel() {
		return model;
	}

	/**
	 * @write the ontology model   EM: testhalber eingefügt für virtuoso
	 */
	public void setModel(OntModel om) {
		this.model = om;
	}
	
	
	/**
	 * @return the stemmer used in the index
	 */
	public Stemmer getStemmer() {
		return stemmer;
	}

	/**
	 * Look up <code>term</code> in the ontology and return a list of similar
	 * concepts (URIs) that corresponds to the term. The result is limited to
	 * <code>limit</code> number of concepts. The order is exact matches,
	 * synonyms, children, parents. The list does not contain duplicates. Never
	 * returns <code>null</code>.
	 * 
	 * @param term
	 *            term to be looked up
	 * @param limit
	 *            maximum number of concepts in the result
	 * @return a list of matching concepts URIs
	 */
	public List<OntClass> getSimilarMatches(String term, int limit) {
		List<OntClass> classes = getExactMatches(term);
		if (log.isDebugEnabled()) {
		    log.debug("****************** EM: getExactMatches (from LabelIndex) for " + term + " = " + OntologyUtils.getLabels(classes));
		}
		// difference between the limit and the current size of the result
		int free;
		
		// check if we reached the limit
		if (classes.size() > limit) {
			classes = classes.subList(0, limit);
			return classes;
		}
			
		
		Set<OntClass> result = new HashSet<OntClass>();
		List<OntClass> tmp;

		result.addAll(classes);
		free = limit - result.size();
		
		// synonyms
		if (free > 0) {
			for (OntClass c : classes) {
				tmp = getSynonyms(c);
				if (tmp.size() > free) {
					result.addAll(tmp.subList(0, free));
					free = 0;
					break;
				} else {
					result.addAll(tmp);
					free = limit - result.size();
				}
			}
		}
		
		// children
		if (free > 0) {
			for (OntClass c : classes) {
				tmp = getChildren(c);
				if (tmp.size() > free) {
					result.addAll(tmp.subList(0, free));
					free = 0;
					break;
				} else {
					result.addAll(tmp);
					free = limit - result.size();
				}
			}
		}
			
		// parents
		if (free > 0) {
			for (OntClass c : classes) {
				tmp = getParents(c);
				if (tmp.size() > free) {
					result.addAll(tmp.subList(0, free));
					free = 0;
					break;
				} else {
					result.addAll(tmp);
					free = limit - result.size();
				}
			}
		}

		return new ArrayList<OntClass>(result);
	}

	/**
	 * Look up <code>term</code> in the ontology and return a list of similar
	 * concepts (URIs) that corresponds to the term. The list does not contain
	 * duplicates. Never returns <code>null</code>.
	 * 
	 * @param term
	 *            term to be looked up
	 * @return a list of matching concepts URIs
	 */
	public List<OntClass> getSimilarMatches(String term) {
		List<OntClass> classes = getExactMatches(term);
		
		Set<OntClass> result = new HashSet<OntClass>();
		result.addAll(classes);
		
		for (OntClass c : classes) {
			result.addAll(getSynonyms(c));
			result.addAll(getChildren(c));
			result.addAll(getParents(c));
		}
		
		return new ArrayList<OntClass>(result);
	}

	/**
	 * Similar to {@link #getSimilarMatches(String)}, but returns the labels
	 * instead of the URIs. The list contains no duplicates.
	 * 
	 * @return labels of the synonyms
	 */
	public List<String> getSimilarMatchLabels(String term) {
		return OntologyUtils.getLabels(getSimilarMatches(term));
	}

	/**
	 * Similar to {@link #getSimilarMatches(String, int)}, but returns the labels
	 * instead of the URIs. The list contains no duplicates.
	 * 
	 * @return labels of the synonyms
	 */
	public List<String> getSimilarMatchLabels(String term, int limit) {
		return OntologyUtils.getLabels(getSimilarMatches(term, limit));
	}

	/**
	 * @param term
	 *            term to be looked up
	 * @return true iff {@link #getFirstExactMatch(String)} does not return
	 *         <code>null</code>.
	 */
	public boolean hasExactMatches(String term) {
		return getFirstExactMatch(term) != null;
	}

	/**
	 * Look up <code>term</code> in the ontology and return a list of concepts
	 * (URIs) that corresponds to the term. It does not search for similar
	 * concepts. Never returns <code>null</code>.
	 * 
	 * @param term
	 *            term to be looked up
	 * @return a list of matching concepts URIs
	 */
	// TODO include a more flexible search using Levenshtein for words with a length > 5
	public List<OntClass> getExactMatches(String term) {
		return getFromLabelIndex(stemmer.stem(term));
	}

	/**
	 * Similar to {@link #getExactMatches(String)}, but returns the labels
	 * instead of the URIs. The list contains no duplicates.
	 * 
	 * @return labels of the synonyms
	 */
	public List<String> getExactMatchLabels(String term) {
		return OntologyUtils.getLabels(getExactMatches(term));
	}

	/**
	 * Similar to {@link #getExactMatches(String)} but does only return the
	 * first match. It returns <code>null</code> if no match can be found.
	 * 
	 * @param term
	 *            term to be looked up
	 * @return first matching concept or <code>null</code>
	 */
	public OntClass getFirstExactMatch(String term) {
		List<OntClass> matches =  getFromLabelIndex(stemmer.stem(term));
		return matches.size() > 0 ? matches.get(0) : null;
	}

	/**
	 * Look up <code>URI</code> in the ontology and return a list of synonyms
	 * (URIs) that corresponds to the term. The matches for term are not
	 * included. The list contains no duplicates. Never returns
	 * <code>null</code>.
	 * 
	 * @param clazz
	 *            term to be looked up
	 * @return a list of synonym concepts URIs
	 */
	public List<OntClass> getSynonyms(OntClass clazz) {
		if (clazz == null)
			return Collections.emptyList();
		
		Set<OntClass> result = new HashSet<OntClass>();

		// the one way
		ExtendedIterator equivIter = clazz.listEquivalentClasses();
		while(equivIter.hasNext()) {
			OntClass synonym = (OntClass)equivIter.next();
			if (!excludeClass(synonym)) {
				result.add(synonym);
			}
		}

		log.debug("*********EM: OntologyIndex.getSynonyms(Synonyme): listEquivalentClasses() für class: "+ clazz.getLocalName() + " = "+ result);		
		return new ArrayList<OntClass>(result);
	}

	/**
	 * Similar to {@link #getSynonyms(OntClass)}, but returns the labels
	 * instead of the URIs. The list contains no duplicates.
	 * 
	 * @return labels of the synonyms
	 */
	public List<String> getSynonymLabels(OntClass clazz) {
		return OntologyUtils.getLabels(getSynonyms(clazz));
	}

	/**
	 * Look up <code>uri</code> in the ontology and return a list of parent
	 * concepts (URIs). Synonyms are not considered. The list contains no
	 * duplicates. Never returns <code>null</code>.
	 * 
	 * @param clazz
	 *            term to be looked up
	 * @return a list of parent concepts URIs
	 */
	// TODO add all synonyms of the parents to the result
	public List<OntClass> getParents(OntClass clazz) {
		if (clazz == null)
			return Collections.emptyList();
		
		List<OntClass> result = new ArrayList<OntClass>();

		ExtendedIterator parentIter = clazz.listSuperClasses(true);
		while (parentIter.hasNext()) {
			OntClass parent = (OntClass) parentIter.next();
			if (log.isDebugEnabled()) {
			    log.debug("************* EM: getParents: for: "+ clazz.getLocalName()+ " = "+ parent.getLocalName()+ " is anonymous Class?: "+ parent.isAnon());
			}
			
			if (excludeClass(parent)) {
			    continue;
			}
			result.add(parent);
		}

		return result;
	}

	/**
	 * Similar to {@link #getParents(OntClass)}, but returns the labels
	 * instead of the URIs. The list contains no duplicates.
	 * 
	 * @return labels of the parents
	 */
	public List<String> getParentLabels(OntClass clazz) {
		return OntologyUtils.getLabels(getParents(clazz));
	}

	/**
	 * Look up <code>clazz</code> in the ontology and return a list of child
	 * concepts. Synonyms are not considered. The list contains no
	 * duplicates. Never returns <code>null</code>.
	 * 
	 * @param clazz
	 *            term to be looked up
	 * @return a list of child concepts URIs
	 */
	// TODO add all synonyms of the children to the result
	public List<OntClass> getChildren(OntClass clazz) {
		if (clazz == null)
			return Collections.emptyList();
		
		List<OntClass> result = new ArrayList<OntClass>();

		ExtendedIterator childIter = clazz.listSubClasses(true);
		while(childIter.hasNext()) {
			OntClass child = (OntClass) childIter.next();
			if (!excludeClass(child)) {
				result.add(child);
			}
		}

		return result;
	}

	/**
	 * Similar to {@link #getChildren(OntClass)} but returns the labels
	 * instead of the URIs. The list contains no duplicates.
	 * 
	 * @return labels of the children
	 */
	public List<String> getChildrenLabels(OntClass clazz) {
		return OntologyUtils.getLabels(getChildren(clazz));
	}

	/**
	 * Load statements from an input stream to the model.
	 * 
	 * @param is
	 *            input stream to read from
	 */
	public void load(InputStream is) {
		model.read(is, "");
		createIndex();
	}

	/**
	 * Load statements from an virtuoso - model.   EM: included for virtuoso
	 * 
	 * @param is
	 *            OntModel om
	 */
	public void load(OntModel om) {
		setModel(om);   // set the model
		log.debug("(re)load model and create onto-index (from virtuoso)");
		createIndex();
	}
	
	
	/**
	 * Adds a new entry to all indexes, e.g., label index, prefix index. The
	 * labels are retrieved from the URI.
	 */
	protected void createIndex() {
		log.debug("Creating index");
		if (model.size() == 0)
			return;
		
		log.debug("***** INDEXES: *****, model has: " + model.size() + " Einträge");        

		ExtendedIterator<OntClass> it = model.listClasses();
		while (it.hasNext()) {
		    OntClass ontClass = it.next();
			
			if (excludeClass(ontClass)) {
			    continue;
			}
			
			List<String> labels = OntologyUtils.getLabels(ontClass);
			log.debug("###############  adding labels " + labels);
			// TODO maybe we should use the GermanAnalyzer at this place to have stop words removed
			for (String label : labels) {
			    log.debug("label: "+label);					
			    addToLabelIndex(stemmer.stem(label), ontClass);
			    addToPrefixIndex(label);
			}
		}
		addIndividualsToIndex();    //EM: add Individual label
		log.debug("done");
log.debug("Anzahl der keys im Labelindex: "+ labelIdx.size());		

	}


	/**
	 * check for classes not to incldie in the index
	 * @param c
	 * @return
	 */
	private boolean excludeClass(OntClass c) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("****** OntClass.localName: %s, with Namespace : %s, with URI: %s",
                         c.getLocalName(), c.getNameSpace(), c.getURI()));
        }

        if (c.isAnon()) {
            log.debug("############### exclude anon " + c);
            return true;
        }
        
        List<String> includeNamespaces = Config.getListProperty(Config.ONTOLOGY_INCLUDED_NAMESPACES);
        if (!includeNamespaces.isEmpty()) {
            // Nur die Klassen aus der Ontologie benutzen
            if (c.getNameSpace() == null || !includeNamespaces.contains(c.getNameSpace())) {
                if (log.isDebugEnabled()) {
                    log.debug("###############  exclude class " + c.getLocalName() + "; namespace not included");
                }
                return true;
            }
        } else {
            List<String> excludeNamespaces = Config.getListProperty(Config.ONTOLOGY_EXCLUDED_NAMESPACES);
            if (c.getNameSpace() == null || excludeNamespaces.contains(c.getNameSpace())) {
                if (log.isDebugEnabled()) {
                    log.debug("###############  exclude class " + c.getLocalName() + "; namespace excluded");
                }
                return true;
            }

        }

        List<String> excludeProperties = Config.getListProperty(Config.ONTOLOGY_IGNORE_PROPERTY);
        if (!excludeProperties.isEmpty()) {
            for (String propURI : excludeProperties) {
                Property property = model.createProperty(propURI);
                if (c.hasLiteral(property, true)) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("###############  exclude %s having property %s", c.getLocalName(), propURI));
                    }
                    return true;
                }
            }
        }

        return false;
    }
	
	//EM:***************************** start ***********************************	
	/**
	 * Adds a new entry to all indexes, e.g., label index, prefix index. The
	 * labels are retrieved from the Individuals.
	 */
	protected void addIndividualsToIndex() {
		log.debug("add Individuals-label to index");
		if (model.size() == 0)
			return;
		

		ExtendedIterator<Individual> ind_it = model.listIndividuals();
		Individual ind;
		while (ind_it.hasNext()) {
			ind = ind_it.next();
	        if (log.isDebugEnabled()) {
                log.debug(String.format("****** Individual.localName: %s, with label: %s, from class: %s ",
                        ind.getLocalName(), OntologyUtils.getLabels(ind), ind.getOntClass().getLocalName()));
            }

	        if (excludeIndividual(ind)) {
	            continue;
	        }
			
			List<String> labels = OntologyUtils.getLabels(ind);
			for (String label : labels) {
				if (label!=null) {
					addToLabelIndex(stemmer.stem(label), ind.getOntClass());
					addToPrefixIndex(label);
				}
			}	
		}
	}

	/**
	 * helper to exclude individuals from the index
	 * @param int the indivdual to test, should not be null
	 * @return true if this individual should be ignored
	 */
	private boolean excludeIndividual(Individual ind) {
        List<String> includeNamespaces = Config.getListProperty(Config.ONTOLOGY_INCLUDED_NAMESPACES);
        if (!includeNamespaces.isEmpty()) {
            if (ind.getNameSpace() != null && includeNamespaces.contains(ind.getNameSpace())) {
                return false;
            }
            ExtendedIterator<OntClass> myclasses = ind.listOntClasses(false); 
            while (myclasses.hasNext()) {
                OntClass indClass = myclasses.next();
                if (!excludeClass(indClass)) {
                    myclasses.close();
                    return true;
                }
            }
            
            if (log.isDebugEnabled()) {
                log.debug("###############  skip class " + ind.getLocalName() + " from index; namespace not included");
            }
            return true;
        }
        return false;
	}
	
	// temporary, to remove *************************************	
	private List<Individual> dumpIndividuals() {	
		ExtendedIterator<Individual> ind_it = model.listIndividuals();
		Individual ind;
		List<Individual> result = new ArrayList<Individual>();
		while (ind_it.hasNext()) {
			ind = ind_it.next();
			if (log.isDebugEnabled()) {
                log.debug(String.format("****** Individual.localName: %s, with label: %s, from class: %s ",
                        ind.getLocalName(), OntologyUtils.getLabels(ind), ind.getOntClass().getLocalName()));
			}
			result.add(ind);
		}
		return result;
	}
	//****************************** end ***********************************	
	
	
	/**
	 * @param term
	 *            a term (can consist of multiple words)
	 * @return true iff prefix is contained in the index.
	 */
	public boolean isPrefix(String term) {
		return isPrefix(Arrays.asList(explode(term)));
	}

	/**
	 * Tests, if the concatenation of the given fragments are contained in the
	 * prefix index. The order is preserved.
	 * 
	 * @param fragments
	 *            a list of terms
	 * @return true iff there is a prefix consisting of
	 */
	public boolean isPrefix(Collection<String> fragments) {
		List<String> stems = new ArrayList<String>();
		for (String f : fragments) {
			stems.add(stemmer.stem(f));
		}
		return prefixIdx.contains(implode(stems));
	}

	/**
	 * Generates the prefixes of a term. If {@link #explode(String)} returns an
	 * array f1..fn with n > 1 then all terms of the form implode(f1..fi) with
	 * 1<=i<n are prefixes (see {@link #implode(Collection)}.
	 * 
	 * @param term
	 *            a term
	 * @return the prefix corresponding to a term.
	 */
	protected List<String> generatePrefixes(String term) {
		if (term == null)
			throw new NullPointerException("Parameter term must not be null");
		
		// TODO normalization of the term, e.g., remove all punctuation, '-', etc.
		log.debug("PrefixIndex.term: "+term);
		String[] fragments = explode(term);
		if (fragments.length <= 1)
			return Collections.emptyList();
		
		List<String> result = new ArrayList<String>();
		String prefix = stemmer.stem(fragments[0]);
		result.add(prefix);
			
		for (int i = 1; i < fragments.length - 1; i++) {
			prefix = implode(prefix, stemmer.stem(fragments[i]));
			result.add(prefix);
		}
		log.debug("PrefixIndex.resultindex: "+result.toString());
		
		return result;
	}

	/**
	 * Split a string into fragment (at whitespaces) in the context of a prefix
	 * index. Not stemmed.
	 * 
	 * @param term
	 *            a term
	 * @return a list of fragments
	 */
	public String[] explode(String term) {
		return StringUtils.split(term);
	}

	/**
	 * Concatenate two fragments in the context of a prefix index.
	 * 
	 * @param f1
	 *            first fragment
	 * @param f2
	 *            second fragment
	 * @return concatenation
	 */
	protected String implode(String f1, String f2) {
		return f1 + PREFIX_SEPARATOR + f2;
	}

	/**
	 * Concatenate fragments in the context of a prefix index.
	 * 
	 * @param c
	 *            collection of fragments
	 * @return concatenation
	 */
	protected String implode(Collection<String> c) {
		return StringUtils.join(c, PREFIX_SEPARATOR);
	}
	
	/**
	 * Convenience method for handling the array of the label index. Adds an
	 * entry into the index. The key is taken as given.
	 * 
	 * @param key
	 *            a label
	 * @param clazz
	 *            the URI of a concept
	 */
	protected void addToLabelIndex(String key, OntClass clazz) {
		Set<OntClass> value = new HashSet<OntClass>();
		if (labelIdx.containsKey(key)) {
			value.addAll(Arrays.asList(labelIdx.get(key)));
		}
		value.add(clazz);
		OntClass[] s = new OntClass[value.size()];
		value.toArray(s);
		
		labelIdx.put(key, s);
		log.trace("** Updated index with " + key + " => " + value);
	}
	
	/**
	 * Adds all prefixes of term to the prefix index.
	 * 
	 * @param term
	 *            a label
	 */
	protected void addToPrefixIndex(String term) {
		List<String> prefixes = generatePrefixes(term);
		if (!prefixes.isEmpty()) {
			prefixIdx.addAll(prefixes);
			log.trace("** Updated prefix with " + prefixes);
		}
	}

	/**
	 * Convenience method for handling the array of the label index.Look up key
	 * in the index and return corresponding URIs. Never returns
	 * <code>null</code>.
	 * 
	 * @param key
	 *            key to be looked up
	 * @return list of corresponding URIs
	 */
	protected List<OntClass> getFromLabelIndex(String key) {
		if (labelIdx.containsKey(key))
//			return Arrays.asList(labelIdx.get(key));      //EM: original
		    return checkAdmin(Arrays.asList(labelIdx.get(key)));  //EM: included for test
		else
			return Collections.emptyList(); 
	}

    boolean isAdmin = false;

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

	private List<OntClass> checkAdmin(List<OntClass> classes){
		List<OntClass> reslist = new ArrayList<OntClass>();
		for(OntClass cl: classes )
		{
		    if (log.isDebugEnabled()) {
		        log.debug("%%%%%%%%%%%% EM: user ist Admin: "+ isAdmin + ", class: "+cl.getURI());
		    }
		    boolean exclude = excludeClass(cl);
		    
			if (isAdmin) {
				if (exclude) {
				    reslist.add(cl);
				}
			}
			else if (!exclude) {
				reslist.add(cl);
			}
		}

		if (log.isDebugEnabled()) {
		    log.debug("%%%%%%%%%%%% EM: liste mit: "+reslist.size()+" Klassen" );
		}
	
		return reslist;
	}
	
	
	/**
	 * Clear ontology model, indexes, and all other stuff.
	 */
	public void reset() {
		model.removeAll();
		labelIdx.clear();
		prefixIdx.clear();
	}
	
	public Map<String, OntClass[]> getLabelIndex() {
		return labelIdx;
	}
	
	public Set<String> getPrefixIndex() {
		return prefixIdx;
	}
}
