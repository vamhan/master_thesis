package model;

import org.springframework.hateoas.ResourceSupport;

public class Instance extends ResourceSupport {

	private String value;

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	
	
}
