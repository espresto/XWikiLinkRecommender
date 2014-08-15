package de.csw.lucene;

import java.util.List;

import com.hp.hpl.jena.ontology.OntClass;

import org.apache.lucene.util.Attribute;

public interface ConceptAttribute extends Attribute {

	public List<OntClass> getConcepts();

	public void setConcepts(List<OntClass> concepts);

	public boolean isConcept();

}