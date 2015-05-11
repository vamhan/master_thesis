package model;

import org.springframework.hateoas.ResourceSupport;

public class EmptyObject extends HALResource {
	private String message;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
