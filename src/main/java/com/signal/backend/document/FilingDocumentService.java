package com.signal.backend.document;

import com.signal.backend.filing.RawFiling;
import com.signal.backend.filing.RawFilingCreatedEvent;
import com.signal.backend.filing.RawFilingRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FilingDocumentService {

    private static final Logger log = LoggerFactory.getLogger(FilingDocumentService.class);
    private static final Pattern ITEM_HEADER =
            Pattern.compile("Item[\\s\\u2009]+(\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String EXHIBITS_ITEM = "9.01";
    private static final Pattern SIGNATURE_BLOCK =
            Pattern.compile("SIGNATURE|pursuant to the requirements", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PARSE_ERROR_LENGTH = 500;

    private final FilingDocumentRepository filingDocumentRepository;
    private final RawFilingRepository rawFilingRepository;
    private final RestClient restClient;

    public FilingDocumentService(FilingDocumentRepository filingDocumentRepository,
                                 RawFilingRepository rawFilingRepository,
                                 RestClient.Builder restClientBuilder) {
        this.filingDocumentRepository = filingDocumentRepository;
        this.rawFilingRepository = rawFilingRepository;
        this.restClient = restClientBuilder
                .baseUrl("https://www.sec.gov")
                .defaultHeader("User-Agent", "Signal signal@example.com")
                .build();
    }

    @Transactional
    public int parseUnparsed() {
        List<RawFiling> unparsed = filingDocumentRepository.findUnparsedRawFilings();
        if (unparsed.isEmpty()) {
            log.info("No unparsed backlog found");
            return 0;
        }

        log.info("Parsing backlog of {} raw filings", unparsed.size());
        int successCount = 0;

        for (RawFiling rawFiling : unparsed) {
            FilingDocument doc = parseSingle(rawFiling);
            filingDocumentRepository.save(doc);
            if (doc.getParseStatus() == FilingDocument.ParseStatus.SUCCESS) {
                successCount++;
            }
        }

        log.info("Backlog parse complete — {}/{} succeeded", successCount, unparsed.size());
        return successCount;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRawFilingCreated(RawFilingCreatedEvent event) {
        Optional<RawFiling> maybeRawFiling = rawFilingRepository.findById(event.rawFilingId());
        if (maybeRawFiling.isEmpty()) {
            log.warn("RawFiling id {} not found during async parse", event.rawFilingId());
            return;
        }

        RawFiling rawFiling = maybeRawFiling.get();
        log.info("RawFilingCreatedEvent received for {}", rawFiling.getAccessionNumber());

        if (filingDocumentRepository.existsByRawFiling(rawFiling)) {
            log.debug("Already parsed {}, skipping", rawFiling.getAccessionNumber());
            return;
        }

        FilingDocument doc = parseSingle(rawFiling);
        filingDocumentRepository.save(doc);
    }

    FilingDocument parseSingle(RawFiling rawFiling) {
        try {
            String indexHtml = fetch(rawFiling.getEdgarUrl());
            Document indexDoc = Jsoup.parse(indexHtml);

            String primaryDocUrl = resolvePrimaryDocUrl(indexDoc);
            if (primaryDocUrl == null) {
                return new FilingDocument(rawFiling,
                        "Could not find 8-K primary document row in index page");
            }

            List<String> exhibitTypes = extractExhibitTypes(indexDoc);
            Integer filingSizeBytes = extractFilingSizeBytes(indexDoc);

            String docHtml = fetch(primaryDocUrl);
            Document doc = Jsoup.parse(docHtml);
            String fullText = doc.body().text();

            Map<String, String> itemTexts = extractItemTexts(fullText);
            List<String> itemNumbers = new ArrayList<>(itemTexts.keySet());
            int wordCount = itemTexts.values().stream()
                    .mapToInt(text -> text.split("\\s+").length)
                    .sum();

            FilingDocument.ParseStatus status = itemTexts.isEmpty()
                    ? FilingDocument.ParseStatus.PARTIAL
                    : FilingDocument.ParseStatus.SUCCESS;

            log.info("Parsed {} — items: {}, words: {}, exhibits: {}",
                    rawFiling.getAccessionNumber(), itemNumbers, wordCount, exhibitTypes);

            return new FilingDocument(
                    rawFiling,
                    primaryDocUrl,
                    itemNumbers.toArray(new String[0]),
                    itemTexts,
                    exhibitTypes.toArray(new String[0]),
                    wordCount,
                    filingSizeBytes,
                    status
            );
        } catch (Exception e) {
            String msg = e.getMessage();
            String error = msg != null
                    ? msg.substring(0, Math.min(msg.length(), MAX_PARSE_ERROR_LENGTH))
                    : e.getClass().getSimpleName();
            log.warn("Failed to parse {}: {}", rawFiling.getAccessionNumber(), error);
            return new FilingDocument(rawFiling, error);
        }
    }

    private String resolvePrimaryDocUrl(Document indexDoc) {
        Elements rows = indexDoc.select("table tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 4 && cells.get(3).text().trim().equals("8-K")) {
                Element link = cells.get(2).selectFirst("a");
                if (link != null) {
                    String href = link.attr("href");
                    if (href.contains("/ix?doc=")) {
                        href = href.substring(href.indexOf("/ix?doc=") + "/ix?doc=".length());
                    }
                    return "https://www.sec.gov" + href;
                }
            }
        }
        return null;
    }

    private List<String> extractExhibitTypes(Document indexDoc) {
        List<String> exhibits = new ArrayList<>();
        Elements rows = indexDoc.select("table tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 4) {
                String type = cells.get(3).text().trim();
                if (type.startsWith("EX-")) {
                    exhibits.add(type);
                }
            }
        }
        return exhibits;
    }

    private Integer extractFilingSizeBytes(Document indexDoc) {
        Elements rows = indexDoc.select("table tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 5 && cells.get(3).text().trim().equals("8-K")) {
                try {
                    return Integer.parseInt(cells.get(4).text().trim().replaceAll(",", ""));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Map<String, String> extractItemTexts(String fullText) {
        Matcher sigMatcher = SIGNATURE_BLOCK.matcher(fullText);
        if (sigMatcher.find()) {
            fullText = fullText.substring(0, sigMatcher.start());
        }

        Map<String, String> itemTexts = new LinkedHashMap<>();
        Matcher itemMatcher = ITEM_HEADER.matcher(fullText);

        List<int[]> positions = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        while (itemMatcher.find()) {
            positions.add(new int[]{itemMatcher.start(), itemMatcher.end()});
            keys.add(itemMatcher.group(1));
        }

        for (int i = 0; i < keys.size(); i++) {
            String itemNumber = keys.get(i);
            if (EXHIBITS_ITEM.equals(itemNumber)) continue;

            int textStart = positions.get(i)[1];
            int textEnd = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : fullText.length();

            String itemText = fullText.substring(textStart, textEnd).strip();
            if (!itemText.isBlank()) {
                itemTexts.put(itemNumber, itemText);
            }
        }

        return itemTexts;
    }

    private String fetch(String url) {
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
    }
}
