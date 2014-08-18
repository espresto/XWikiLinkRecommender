package de.csw.lucene;

import java.util.List;

import com.hp.hpl.jena.ontology.OntResource;

import org.apache.lucene.util.AttributeImpl;

public class ConceptAttributeImpl extends AttributeImpl implements ConceptAttribute {

	private List<OntResource> concepts;

	public List<OntResource> getConcepts() {
		return concepts;
	}

	public void setConcepts(List<OntResource> concepts) {
		this.concepts = concepts;
	}

	public boolean isConcept() {
		return concepts != null && !concepts.isEmpty();
	}

	@Override
	public void clear() {
		concepts = null;
	}

	@Override
	public void copyTo(AttributeImpl target) {
		ConceptAttributeImpl cTarget = ((ConceptAttributeImpl) target);
		cTarget.concepts = concepts;
	}

}
