package com.signal.backend.edgar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EdgarClient {
    private static final Logger log = LoggerFactory.getLogger(EdgarClient.class);

    private static final String FEED_BASE_URL =
            "https://www.sec.gov/cgi-bin/browse-edgar" +
            "?action=getcurrent&type=8-K&owner=include&output=atom&count=100";
    
    // Title format: "8-K - Current report - COMPANY NAME (0001234567) (8-K)"
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("^[^-]+-[^-]+-\\s*(.+?)\\s*\\((\\d+)\\)");

    // id format: "urn:tag:sec.gov,2008:accession-number=0001234567-24-000001"
    private static final Pattern ACCESSION_PATTERN =
            Pattern.compile("accession-number=([\\d-]+)$");
    
    private final RestClient restClient;

    public EdgarClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://www.sec.gov")
                .defaultHeader("User-Agent", "Signal signal@example.com")
                .defaultHeader("Accept-Encoding", "gzip, deflate")
                .build();
    }

    /**
     * Fetches one page of 8-K filings from the EDGAR Atom feed.
     *
     * @param start zero-based offset for pagination (0, 100, 200, ...)
     * @return parsed entries for this page
     */
    public List<EdgarFeedEntry> fetchPage(int start) {
        String url = FEED_BASE_URL + "&start=" + start;
        log.info("Fetching EDGAR 8-K feed: start={}", start);

        String xml = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
        
        if (xml == null || xml.isBlank()) {
            log.warn("EDGAR RSS feed returned empty response at start={}", start);
            return List.of();
        }
        
        return parseAtomFeed(xml);
    }

    private List<EdgarFeedEntry> parseAtomFeed(String xml) {
        List<EdgarFeedEntry> entries = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            
            NodeList entryNodes = doc.getElementsByTagName("entry");

            for (int i = 0; i < entryNodes.getLength(); i++) {
                Element entry = (Element) entryNodes.item(i);

                String id = getTagText(entry, "id");
                String title = getTagText(entry, "title");
                String updated = getTagText(entry, "updated");
                String link = getLinkHref(entry);

                if (id == null || title == null || updated == null || link == null) {
                    log.warn("Skipping entry with missing required fields: id={}", id);
                    continue;
                }

                Matcher accMatcher = ACCESSION_PATTERN.matcher(id);
                if (!accMatcher.find()) {
                    log.warn("Could not parse accession number from id: {}", id);
                    continue;
                }
                String accessionNumber = accMatcher.group(1);

                Matcher titleMatcher = TITLE_PATTERN.matcher(title);
                if (!titleMatcher.find()) {
                    log.warn("Could not parse title: {}", title);
                    continue;
                }
                String companyName = titleMatcher.group(1).trim();
                String cik = titleMatcher.group(2).replaceFirst("^0+", "");

                // Keep full feed timestamp; downstream converts as needed.
                OffsetDateTime filedAt = OffsetDateTime.parse(updated);

                entries.add(new EdgarFeedEntry(
                        accessionNumber,
                        cik,
                        companyName,
                        "8-K",
                        link,
                        filedAt
                ));
            }
        } catch (Exception e) {
            log.error("Failed to parse EDGAR RSS feed", e);
        }
        log.info("Parsed {} entries from EDGAR feed page", entries.size());
        return entries;
    }

    private String getTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private String getLinkHref(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            if (href != null && !href.isBlank()) return href;
        }
        return null;
    }
}