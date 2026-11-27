package me.cookie.kwrg.svg;

import me.cookie.kwrg.svg.style.SvgStyle;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SvgParser {

    public static final int MAX_SOURCE = 65536;
    public static final int MAX_NODES = 4000;
    public static final int MAX_DEPTH = 48;
    public static final int MAX_ATTRIBUTE = 8192;

    private static final String[] FORBIDDEN = {"<!doctype", "<!entity", "<?xml-stylesheet"};

    public record Document(SvgNode root, List<SvgStyle.Rule> rules) {
    }

    private SvgParser() {
    }

    public static Document parse(String source) {
        if (source == null) {
            throw new IllegalArgumentException("empty svg");
        }
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty svg");
        }
        if (trimmed.length() > MAX_SOURCE) {
            throw new IllegalArgumentException("svg too large");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN) {
            if (lower.contains(forbidden)) {
                throw new IllegalArgumentException("forbidden svg construct");
            }
        }
        Element element = build(trimmed);
        if (element == null) {
            throw new IllegalArgumentException("svg root missing");
        }
        List<SvgStyle.Rule> rules = new ArrayList<>();
        Map<String, SvgNode> index = new HashMap<>();
        int[] budget = {MAX_NODES};
        SvgNode root = convert(element, rules, index, budget, 0);
        if (root == null) {
            throw new IllegalArgumentException("svg root missing");
        }
        root.index(index);
        return new Document(root, rules);
    }

    private static Element build(String source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(null);
            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(source)));
            Element root = document.getDocumentElement();
            if (root == null) {
                return null;
            }
            String name = localName(root.getNodeName());
            if (!"svg".equals(name)) {
                NodeList children = root.getElementsByTagName("svg");
                if (children.getLength() == 0) {
                    return null;
                }
                return (Element) children.item(0);
            }
            return root;
        } catch (Exception e) {
            throw new IllegalArgumentException("svg xml invalid", e);
        }
    }

    private static SvgNode convert(Element element, List<SvgStyle.Rule> rules, Map<String, SvgNode> index, int[] budget, int depth) {
        if (depth > MAX_DEPTH || budget[0] <= 0) {
            return null;
        }
        budget[0]--;
        Map<String, String> attributes = new LinkedHashMap<>();
        NamedNodeMap raw = element.getAttributes();
        for (int i = 0; i < raw.getLength(); i++) {
            Node attribute = raw.item(i);
            String name = localName(attribute.getNodeName());
            String value = attribute.getNodeValue();
            if (name == null || value == null) {
                continue;
            }
            if (value.length() > MAX_ATTRIBUTE) {
                value = value.substring(0, MAX_ATTRIBUTE);
            }
            attributes.put(name.toLowerCase(Locale.ROOT), value);
        }
        SvgNode node = new SvgNode(localName(element.getNodeName()).toLowerCase(Locale.ROOT), attributes);
        String id = node.id();
        if (id != null && !id.isBlank()) {
            index.putIfAbsent(id.trim().toLowerCase(Locale.ROOT), node);
        }
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            short kind = child.getNodeType();
            if (kind == Node.TEXT_NODE || kind == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            } else if (kind == Node.ELEMENT_NODE) {
                if ("style".equals(localName(child.getNodeName()))) {
                    rules.addAll(SvgStyle.parseCss(child.getTextContent()));
                    continue;
                }
                SvgNode converted = convert((Element) child, rules, index, budget, depth + 1);
                if (converted != null) {
                    node.add(converted);
                }
            }
        }
        if (!text.isEmpty()) {
            node.text(text.toString().trim());
        }
        return node;
    }

    private static String localName(String name) {
        if (name == null) {
            return null;
        }
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
