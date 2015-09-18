package eu.comsode.unifiedviews.plugins.extractor.skmartincontracts;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.unifiedviews.dataunit.DataUnit;
import eu.unifiedviews.dataunit.rdf.WritableRDFDataUnit;
import eu.unifiedviews.dpu.DPU;
import eu.unifiedviews.dpu.DPUException;
import eu.unifiedviews.helpers.dpu.config.ConfigHistory;
import eu.unifiedviews.helpers.dpu.context.ContextUtils;
import eu.unifiedviews.helpers.dpu.exec.AbstractDpu;
import eu.unifiedviews.helpers.dpu.rdf.EntityBuilder;

/**
 * Main data processing unit class.
 */
@DPU.AsExtractor
public class SkMartinContracts extends AbstractDpu<SkMartinContractsConfig_V1> {

    private static final Logger LOG = LoggerFactory.getLogger(SkMartinContracts.class);

    private static final String INPUT_URL = "http://egov.martin.sk/Default.aspx?NavigationState=778:0:";

    private static final String BASE_URI = "http://localhost/";

    private static final String PURL_URI = "http://purl.org/procurement/public-contracts#";

    private Map<String, Integer> keys = new HashMap<String, Integer>();

    @DataUnit.AsOutput(name = "rdfOutput")
    public WritableRDFDataUnit rdfOutput;

    private static String nhs = null;

    private static Element lf = null;

    private static Element pps = null;

    private static Element vs = null;

    private static Element vsg = null;

    private static Element ev = null;

    private static Element wmsca = null;

    private static Element ppgts = null;

    private String wmSavedSa = null;

    private Map<String, String> prsdrsp = null;

    private static String clsDetailSM = "WM$winDet144101$hidUP";

    private static String nextPageSM1 = "Portal1$part1889$up";

    private static String nextPageSM2 = "Portal1$part1889$grid$pageTbr";

    public SkMartinContracts() {
        super(SkMartinContractsVaadinDialog.class, ConfigHistory.noHistory(SkMartinContractsConfig_V1.class));
    }

    @Override
    protected void innerExecute() throws DPUException {
        initializeKeysMap();
        RepositoryConnection connection = null;
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            org.openrdf.model.URI graph = rdfOutput.addNewDataGraph("skMartinContractsRdfData");
            connection = rdfOutput.getConnection();
            ValueFactory vf = ValueFactoryImpl.getInstance();

            HttpGet httpGet = new HttpGet(INPUT_URL);
            httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            httpGet.setHeader("Accept-Encoding", "gzip, deflate");
            httpGet.setHeader("Accept-Language", "en-US,cs;q=0.7,en;q=0.3");
            httpGet.setHeader("Connection", "keep-alive");
            httpGet.setHeader("Host", (new URL(INPUT_URL)).getHost()); //"egov.martin.sk"
            httpGet.setHeader("Referer", INPUT_URL);
            httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:38.0) Gecko/20100101 Firefox/38.0");
            CloseableHttpResponse response1 = httpclient.execute(httpGet);

            LOG.debug(String.format("GET response status line: %s", response1.getStatusLine()));
            int responseCode = response1.getStatusLine().getStatusCode();
            StringBuilder headerSb = new StringBuilder();
            for (Header h : response1.getAllHeaders()) {
                headerSb.append("Key : " + h.getName() + " ,Value : " + h.getValue());
            }
            LOG.debug(headerSb.toString());

            Header[] cookies = response1.getHeaders("Set-Cookie");
            String[] cookieParts = cookies[0].getValue().split("; ");
            String sessionId = cookieParts[0];
            String response = null;
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOG.error("GET request not worked");
                throw new Exception("GET request not worked");
            }
            HttpEntity entity = null;
            try {
                entity = response1.getEntity();
                response = EntityUtils.toString(entity);
            } finally {
                EntityUtils.consumeQuietly(entity);
                response1.close();
            }
            int counter = 0;
            do {
                LOG.debug(String.format("Server response:\n%s", response));
                Document doc = Jsoup.parse(response);

                Element content = doc.select("table#Portal1_part1889_grid_grdTable").first();
                if (content == null) {
                    throw new Exception("Error scrapping page. The content of the page is empty.");
                }
                Element body = content.select("tbody").first();
                Elements links = body.select("tr");
                for (Element link : links) {
                    Element onClick = link.select("div.DetailIcon").first();
                    String postBack = onClick.attr("onclick");
                    Pattern pattern = Pattern.compile("\\'.*?\\'");
                    Matcher matcher = pattern.matcher(postBack);

                    List<String> params = new ArrayList<String>();
                    while (matcher.find()) {
                        params.add(postBack.substring(matcher.start() + 1, matcher.end() - 1));
                    }
                    Map<String, String> detailPostParamsMap = prepareHttpPostParamsMap(doc);
                    detailPostParamsMap.put("ScriptManager1", params.get(0) + "|" + params.get(0));
                    detailPostParamsMap.put("__EVENTTARGET", params.get(0));
                    detailPostParamsMap.put("__EVENTARGUMENT", params.get(1));
                    String contractDetail = getDetailInfo(httpclient, sessionId, detailPostParamsMap);

                    prsdrsp = parseDetailListResponse(contractDetail);
                    Element contractDetailContent = Jsoup.parse(contractDetail);
                    wmSavedSa = contractDetailContent.select("input#WM_savedSA").first().attr("value");

                    Element detailTable = contractDetailContent.select("table.detTbl").first();

                    UUID uuid = UUID.randomUUID();
                    org.openrdf.model.URI uri = vf.createURI(BASE_URI + uuid.toString());
                    EntityBuilder eb = new EntityBuilder(uri, vf);
                    eb.property(RDF.TYPE, vf.createURI(PURL_URI + "Contract"));

                    eb = getDetails(detailTable, eb, vf);
                    connection.add(eb.asStatements(), graph);
                    counter++;
                    LOG.info("Done contract {}", counter);
                }
                Element pagingControl = doc.select("span.PagingControl").first();
                int activePage = Integer.parseInt(pagingControl.attr("actPage"));
                int maxPage = Integer.parseInt(pagingControl.attr("maxPage"));
                LOG.debug("Processed " + activePage + " of " + maxPage + " pages.");
                if (activePage == maxPage) {
                    break;
                }
                Map<String, String> nextPagePostParamsMap = new HashMap<String, String>();
                nextPagePostParamsMap.put("ScriptManager1", nextPageSM1 + "|" + nextPageSM2);
                nextPagePostParamsMap.put("NavigationHistoryState", nhs);
                nextPagePostParamsMap.put("WM$savedSA", "");
                nextPagePostParamsMap.put("__EVENTTARGET", nextPageSM2);
                nextPagePostParamsMap.put("__EVENTARGUMENT", Integer.toString(activePage + 1));
                nextPagePostParamsMap.put("__VIEWSTATE", prsdrsp.get("__VIEWSTATE"));
                nextPagePostParamsMap.put("__VIEWSTATEGENERATOR", prsdrsp.get("__VIEWSTATEGENERATOR"));
                nextPagePostParamsMap.put("__EVENTVALIDATION", prsdrsp.get("__EVENTVALIDATION"));
                nextPagePostParamsMap.put("__PortalPageState", prsdrsp.get("__PortalPageState"));
                nextPagePostParamsMap.put("__ASYNCPOST", "true");
                response = getDetailInfo(httpclient, sessionId, nextPagePostParamsMap);
                prsdrsp = parseDetailListResponse(response);
            } while (true);
        } catch (Exception ex) {
            throw ContextUtils.dpuException(ctx, ex, "SkMartinContracts.execute.exception");
        }

    }

    private String normalizeSum(String sum) {
        String sumToConvert = sum.replaceAll(" ", "").replaceAll(",", ".").trim();
        Double result = null;
        try {
            result = Double.parseDouble(sumToConvert);
        } catch (NumberFormatException ex) {
            LOG.error(String.format("Problem converting string %s to Double.", sumToConvert), ex);
        }
        return result.toString();
    }

    private String getDetailInfo(CloseableHttpClient client, String sessionId, Map<String, String> postParams) throws IOException {
        HttpPost httpPost = new HttpPost(INPUT_URL);
        httpPost.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        httpPost.setHeader("Accept-Encoding", "gzip, deflate");
        httpPost.setHeader("Accept-Language", "en-US,cs;q=0.7,en;q=0.3");
        httpPost.setHeader("Cache-Control", "no-cache");
        httpPost.setHeader("Connection", "keep-alive");
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        httpPost.setHeader("Cookie", sessionId + "; ys-browserCheck=b%3A1");
        httpPost.setHeader("Host", (new URL(INPUT_URL)).getHost());
        httpPost.setHeader("Pragma", "no-cache");
        httpPost.setHeader("Referer", (new URL(INPUT_URL)).getHost());
        httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:38.0) Gecko/20100101 Firefox/38.0");
        httpPost.setHeader("X-MicrosoftAjax", "Delta=true");
        httpPost.setHeader("X-Requested-With", "XMLHttpRequest");

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> postParam : postParams.entrySet()) {
            nvps.add(new BasicNameValuePair(postParam.getKey(), postParam.getValue()));
        }
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        String responseDoc = null;
        try (CloseableHttpResponse response2 = client.execute(httpPost)) {

            LOG.debug("POST Response Code :: " + response2.getStatusLine().getStatusCode());

            LOG.debug("Printing Response Header...\n");
            StringBuilder headerSb = new StringBuilder();
            for (Header h : response2.getAllHeaders()) {
                headerSb.append("Key : " + h.getName() + " ,Value : " + h.getValue());
            }
            LOG.debug(headerSb.toString());
            HttpEntity entity = null;
            try {
                entity = response2.getEntity();
                if (response2.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_OK) { //success
                    responseDoc = EntityUtils.toString(entity);
                } else {
                    LOG.error("POST request not worked");
                }
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        }
        return responseDoc;
    }

    private Map<String, String> prepareHttpPostParamsMap(Element doc) {
        Map<String, String> postParams = new HashMap<String, String>();
        if (doc.select("input#NavigationHistoryState").first() != null) {
            nhs = doc.select("input#NavigationHistoryState").first().attr("value");
        }
        if (nhs != null) {
            postParams.put("NavigationHistoryState", nhs);
        }
        if (doc.select("input#__LASTFOCUS").first() != null) {
            lf = doc.select("input#__LASTFOCUS").first();
        }
        if (lf != null) {
            postParams.put("__LASTFOCUS", lf.attr("value"));
        }
        if (doc.select("input#__PortalPageState").first() != null) {
            pps = doc.select("input#__PortalPageState").first();
        }
        if (pps != null) {
            postParams.put("__PortalPageState", pps.attr("value"));
        }
        if (doc.select("input#__VIEWSTATE").first() != null) {
            vs = doc.select("input#__VIEWSTATE").first();
        }
        if (vs != null) {
            postParams.put("__VIEWSTATE", vs.attr("value"));
        }
        if (doc.select("input#__VIEWSTATEGENERATOR").first() != null) {
            vsg = doc.select("input#__VIEWSTATEGENERATOR").first();
        }
        if (vsg != null) {
            postParams.put("__VIEWSTATEGENERATOR", vsg.attr("value"));
        }
        if (doc.select("input#__EVENTVALIDATION").first() != null) {
            ev = doc.select("input#__EVENTVALIDATION").first();
        }
        if (ev != null) {
            postParams.put("__EVENTVALIDATION", ev.attr("value"));
        }
        if (doc.select("input#WM_savedSA").first() != null) {
            wmsca = doc.select("input#WM_savedSA").first();
        }
        if (wmsca != null) {
            postParams.put("WM$savedSA", wmsca.attr("value"));
        }
        if (doc.select("input#Portal1_part2117_grid_txtSearch").first() != null) {
            ppgts = doc.select("input#Portal1_part2117_grid_txtSearch").first();
        }
        if (ppgts != null) {
            postParams.put("Portal1_part2117_grid_txtSearch", ppgts.attr("value"));
        }
        postParams.put("__ASYNCPOST", "true");
        return postParams;
    }

    private EntityBuilder getDetails(Element detailTable, EntityBuilder eb, ValueFactory vf) throws Exception {
        Elements trs = detailTable.select("tr");
        for (Element tr : trs) {
            Elements documentLinks = tr.select("a");
            Element detTitle = tr.select("span.detTitle").first();
            String normalisedDetText = slugify(detTitle.text());
            if (!keys.containsKey(normalisedDetText)) {
                LOG.error("Unknown key: " + normalisedDetText);
            }

            Element spanValue = tr.select("span.detText").first();
            if (documentLinks != null && documentLinks.size() > 0) {
                for (Element documentLink : documentLinks) {
                    if (!StringUtils.isBlank(documentLink.attr("href"))) {
                        keys.put(normalisedDetText, keys.get(normalisedDetText) + 1);
                        eb.property(vf.createURI(BASE_URI + normalisedDetText), "http://" + (new URL(INPUT_URL)).getHost() + "/" + documentLink.attr("href"));
                    }
                }
            } else if (detTitle.text().equals("Cena celkom") && spanValue != null && !StringUtils.isBlank(spanValue.text())) {
                keys.put(normalisedDetText, keys.get(normalisedDetText) + 1);
                eb.property(vf.createURI(BASE_URI + normalisedDetText), normalizeSum(spanValue.text()));
            } else {
                if (spanValue != null && !StringUtils.isBlank(spanValue.text())) {
                    keys.put(normalisedDetText, keys.get(normalisedDetText) + 1);
                    eb.property(vf.createURI(BASE_URI + normalisedDetText), spanValue.text());
                }
            }
        }
        return eb;
    }

    private Map<String, String> parseDetailListResponse(String resp) {
        Pattern pattern = Pattern.compile("[0-9]+\\|[^\\|]*\\|[^\\|]*\\|[^\\|]*\\|");
        Matcher matcher = pattern.matcher(resp);

        Map<String, String> params = new HashMap<String, String>();
        while (matcher.find()) {
            String line = resp.substring(matcher.start(), matcher.end());
            List<String> values = new ArrayList<String>();
            while (line.contains("|")) {
                values.add(line.substring(0, line.indexOf("|")));
                line = line.substring(line.indexOf("|") + 1);
            }
            if (values.size() == 4 && values.get(1).equals("hiddenField")) {
                params.put(values.get(2), values.get(3));
            }
        }
        return params;
    }

    private Map<String, String> closeDetailPostParamsMap() {
        Map<String, String> closeDetailPostParamsMap = new HashMap<String, String>();
        closeDetailPostParamsMap.put("ScriptManager1", clsDetailSM + "|" + clsDetailSM);
        closeDetailPostParamsMap.put("NavigationHistoryState", nhs);
        closeDetailPostParamsMap.put("WM$savedSA", wmSavedSa);
        closeDetailPostParamsMap.put("__EVENTTARGET", clsDetailSM);
        closeDetailPostParamsMap.put("__EVENTARGUMENT", "close");
        closeDetailPostParamsMap.put("__VIEWSTATE", prsdrsp.get("__VIEWSTATE"));
        closeDetailPostParamsMap.put("__VIEWSTATEGENERATOR", prsdrsp.get("__VIEWSTATEGENERATOR"));
        closeDetailPostParamsMap.put("__EVENTVALIDATION", prsdrsp.get("__EVENTVALIDATION"));
        closeDetailPostParamsMap.put("__PortalPageState", prsdrsp.get("__PortalPageState"));
        closeDetailPostParamsMap.put("__ASYNCPOST", "true");
        return closeDetailPostParamsMap;

    }

    private void initializeKeysMap() {
        keys.put("zmluvne-strany-adresy", 0);
        keys.put("centralne-cislo-zmluvy", 0);
        keys.put("datum-zverej-2-zm-stranou", 0);
        keys.put("rok", 0);
        keys.put("nazov", 0);
        keys.put("typ", 0);
        keys.put("druh", 0);
        keys.put("zmluvne-strany", 0);
        keys.put("predmet", 0);
        keys.put("cena-celkom", 0);
        keys.put("mena", 0);
        keys.put("datum-podpisu", 0);
        keys.put("datum-zverejnenia", 0);
        keys.put("dokumenty", 0);
        keys.put("datum-ukoncenia", 0);
        keys.put("ucinnost-od", 0);
        keys.put("datum-povolenia-katastra", 0);
        keys.put("ucinnost-do", 0);
        keys.put("datum-ucinnosti-zmluvy-po-zverejneni", 0);
        keys.put("datum-vypovedania", 0);
        keys.put("miesto-podpisu", 0);
        keys.put("pocet-rokov-gen-predp", 0);
        keys.put("rocny-predpis", 0);
        keys.put("hlavna-dodatok", 0);
        keys.put("platca", 0);
        keys.put("zhotovitel-zmluvy", 0);
        keys.put("majitel-zmluvy", 0);
        keys.put("cislo-zmluvy", 0);
        keys.put("poznamky-k-zverejneniu", 0);
        keys.put("centralny-rok-zmluvy", 0);
    }

    private String slugify(String input) {
        String result = StringUtils.stripAccents(input);
        result = StringUtils.lowerCase(result).trim();
        result = result.replaceAll("[^a-zA-Z0-9\\s]", "");
        result = result.replaceAll("\\b\\s+", "-");
        return result;

    }

}
