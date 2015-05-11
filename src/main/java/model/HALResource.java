package model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.hateoas.ResourceSupport;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class HALResource extends ResourceSupport {

    private final Map<String, List<Map<String, String>>> embedded = new HashMap<String, List<Map<String, String>>>();

    @JsonProperty("_embedded")
    public Map<String, List<Map<String, String>>> getEmbeddedResources() {
        return embedded;
    }

    public void embedResource(String relationship, List<Map<String, String>> resources) {
        embedded.put(relationship, resources);
    }  
}