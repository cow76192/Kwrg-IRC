package me.cookie.kwrg.svg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SvgNode {

    private final String tag;
    private final Map<String, String> attributes;
    private final List<SvgNode> children = new ArrayList<>();
    private SvgNode parent;
    private String text;
    private Map<String, SvgNode> index = Map.of();

    public SvgNode(String tag, Map<String, String> attributes) {
        this.tag = tag;
        this.attributes = attributes;
    }

    public String tag() {
        return tag;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public List<SvgNode> children() {
        return children;
    }

    public String attribute(String key) {
        String direct = attributes.get(key);
        if (direct != null) {
            return direct;
        }
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        String folded = attributes.get(lower);
        if (folded != null) {
            return folded;
        }
        for (java.util.Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String id() {
        return attributes.get("id");
    }

    public SvgNode parent() {
        return parent;
    }

    public String text() {
        return text;
    }

    public void text(String value) {
        this.text = value;
    }

    public Map<String, SvgNode> index() {
        return index;
    }

    public void index(Map<String, SvgNode> value) {
        this.index = value;
    }

    public void add(SvgNode child) {
        child.parent = this;
        children.add(child);
    }
}
