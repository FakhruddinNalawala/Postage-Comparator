package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import org.xml.sax.InputSource;

/**
 * Aramex connector implementation using the RateCalculator SOAP service.
 * Uses credentials and account info from environment variables.
 *
 * Required env vars:
 * - ARAMEX_USERNAME
 * - ARAMEX_PASSWORD
 * - ARAMEX_ACCOUNT_NUMBER
 * - ARAMEX_ACCOUNT_PIN
 * - ARAMEX_ACCOUNT_ENTITY
 * - ARAMEX_ACCOUNT_COUNTRY
 *
 * Optional env vars (defaults from sample client):
 * - ARAMEX_PRODUCT_GROUP (default EXP)
 * - ARAMEX_PRODUCT_TYPE (default PDX)
 * - ARAMEX_PAYMENT_TYPE (default P)
 */
@Component
public class AramexProvider implements CarrierProvider {

    private static final Logger log = LoggerFactory.getLogger(AramexProvider.class);

    private final WebClient aramexWebClient;
    private final QuoteRequestHelper requestHelper;

    public AramexProvider(@Qualifier("aramexWebClient") WebClient aramexWebClient,
                          QuoteRequestHelper requestHelper) {
        this.aramexWebClient = aramexWebClient;
        this.requestHelper = requestHelper;
    }

    @Override
    public String getName() {
        return "aramex";
    }

    @Override
    public boolean isEnabled(ProviderConfig config) {
        return config.getProvider(getName())
                .map(ProviderConfig.ProviderSettings::enabled)
                .orElse(false);
    }

    @Override
    public Optional<CarrierQuote> quote(ShipmentRequest request,
                                        OriginSettings origin,
                                        Packaging packaging,
                                        List<Item> items) {
        String username = env("ARAMEX_USERNAME");
        String password = env("ARAMEX_PASSWORD");
        String accountNumber = env("ARAMEX_ACCOUNT_NUMBER");
        String accountPin = env("ARAMEX_ACCOUNT_PIN");
        String accountEntity = env("ARAMEX_ACCOUNT_ENTITY");
        String accountCountry = envOrDefault("ARAMEX_ACCOUNT_COUNTRY", "AU");

        if (isBlank(username, password)) {
            log.info("Aramex credentials not configured; skipping API call.");
            return Optional.empty();
        }

        QuoteResult.Destination destination = requestHelper.buildDestination(request);
        int totalWeightGrams = requestHelper.calculateTotalWeight(request.items());
        int totalPieces = Math.max(1, request.items().stream().mapToInt(selection -> selection.quantity()).sum());
        double weightKg = totalWeightGrams / 1000.0;

        String productGroup = envOrDefault("ARAMEX_PRODUCT_GROUP", "EXP");
        String productType = envOrDefault("ARAMEX_PRODUCT_TYPE", "PPX");
        String paymentType = envOrDefault("ARAMEX_PAYMENT_TYPE", "P");
        String version = envOrDefault("ARAMEX_VERSION", "v1.0");

        String requestXml = buildRateRequestXml(
                username,
                password,
                version,
                accountNumber,
                accountPin,
                accountEntity,
                accountCountry,
                origin,
                destination,
                packaging,
                weightKg,
                totalPieces,
                productGroup,
                productType,
                paymentType
        );

        try {
            String responseXml = aramexWebClient
                    .post()
                    .contentType(MediaType.TEXT_XML)
                    .accept(MediaType.TEXT_XML)
                    .header("SOAPAction", "http://ws.aramex.net/ShippingAPI/v1/Service_1_0/CalculateRate")
                    .bodyValue(requestXml)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseXml == null || responseXml.isBlank()) {
                log.warn("Aramex API returned empty response");
                return Optional.empty();
            }

            return parseRateResponse(responseXml, packaging);
        } catch (WebClientResponseException e) {
            log.error("Aramex API call failed (status: {}). Response: {}. Stack: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    summarizeStackTrace(e));
            return Optional.empty();
        } catch (WebClientException e) {
            log.error("Aramex API network/client error: {}. Stack: {}", e.getMessage(), summarizeStackTrace(e));
            return Optional.empty();
        } catch (Exception e) {
            log.error("Aramex API call failed: {}. Stack: {}", e.getMessage(), summarizeStackTrace(e));
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<CarrierQuote>> quotes(ShipmentRequest request,
                                               OriginSettings origin,
                                               Packaging packaging,
                                               List<Item> items) {
        return Optional.empty();
    }

    private Optional<CarrierQuote> parseRateResponse(String responseXml, Packaging packaging) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new InputSource(new StringReader(responseXml)));

            XPath xpath = XPathFactory.newInstance().newXPath();
            String hasErrors = (String) xpath.evaluate("//*[local-name()='HasErrors']/text()", doc, XPathConstants.STRING);
            if ("true".equalsIgnoreCase(hasErrors)) {
                String message = (String) xpath.evaluate("//*[local-name()='Notifications']//*[local-name()='Message']/text()",
                        doc, XPathConstants.STRING);
                log.warn("Aramex rate response has errors: {}", message);
                return Optional.empty();
            }

            String currency = (String) xpath.evaluate("//*[local-name()='TotalAmount']/*[local-name()='CurrencyCode']/text()",
                    doc, XPathConstants.STRING);
            String value = (String) xpath.evaluate("//*[local-name()='TotalAmount']/*[local-name()='Value']/text()",
                    doc, XPathConstants.STRING);

            Double totalCost = parseDouble(value);
            if (totalCost == null) {
                log.warn("Aramex rate response missing TotalAmount");
                return Optional.empty();
            }

            if (currency != null && !currency.isBlank() && !currency.equalsIgnoreCase("AUD")) {
                log.warn("Aramex rate response currency not supported: {}", currency);
                return Optional.empty();
            }

            double packagingCost = packaging.packagingCostAud();
            double deliveryCost = totalCost - packagingCost;

            return Optional.of(new CarrierQuote(
                    "ARAMEX",
                    "ARAMEX",
                    null,
                    null,
                    packagingCost,
                    deliveryCost,
                    0.0,
                    totalCost,
                    "ARAMEX_API",
                    false,
                    null
            ));
        } catch (Exception e) {
            log.error("Failed to parse Aramex rate response: {}. Stack: {}", e.getMessage(), summarizeStackTrace(e));
            return Optional.empty();
        }
    }

    private String buildRateRequestXml(String username,
                                       String password,
                                       String version,
                                       String accountNumber,
                                       String accountPin,
                                       String accountEntity,
                                       String accountCountry,
                                       OriginSettings origin,
                                       QuoteResult.Destination destination,
                                       Packaging packaging,
                                       double weightKg,
                                       int pieces,
                                       String productGroup,
                                       String productType,
                                       String paymentType) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:typ="http://ws.aramex.net/ShippingAPI/v1/">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <typ:RateCalculatorRequest>
                      <typ:ClientInfo>
                        <typ:AccountCountryCode>%s</typ:AccountCountryCode>
                        <typ:AccountEntity>%s</typ:AccountEntity>
                        <typ:AccountNumber>%s</typ:AccountNumber>
                        <typ:AccountPin>%s</typ:AccountPin>
                        <typ:UserName>%s</typ:UserName>
                        <typ:Password>%s</typ:Password>
                        <typ:Version>%s</typ:Version>
                      </typ:ClientInfo>
                      <typ:Transaction>
                        <typ:Reference1>001</typ:Reference1>
                      </typ:Transaction>
                      <typ:OriginAddress>
                        <typ:City>%s</typ:City>
                        <typ:CountryCode>%s</typ:CountryCode>
                      </typ:OriginAddress>
                      <typ:DestinationAddress>
                        <typ:City>%s</typ:City>
                        <typ:CountryCode>%s</typ:CountryCode>
                      </typ:DestinationAddress>
                      <typ:ShipmentDetails>
                        <typ:PaymentType>%s</typ:PaymentType>
                        <typ:ProductGroup>%s</typ:ProductGroup>
                        <typ:ProductType>%s</typ:ProductType>
                        <typ:ActualWeight>
                          <typ:Value>%.3f</typ:Value>
                          <typ:Unit>KG</typ:Unit>
                        </typ:ActualWeight>
                        <typ:ChargeableWeight>
                          <typ:Value>%.3f</typ:Value>
                          <typ:Unit>KG</typ:Unit>
                        </typ:ChargeableWeight>
                        <typ:NumberOfPieces>%d</typ:NumberOfPieces>
                      </typ:ShipmentDetails>
                    </typ:RateCalculatorRequest>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(
                escapeXml(accountCountry),
                escapeXml(accountEntity),
                escapeXml(accountNumber),
                escapeXml(accountPin),
                escapeXml(username),
                escapeXml(password),
                escapeXml(version),
                escapeXml(nullToEmpty(origin.suburb())),
                escapeXml(nullToEmpty(origin.country())),
                escapeXml(nullToEmpty(destination.suburb())),
                escapeXml(nullToEmpty(destination.country())),
                escapeXml(paymentType),
                escapeXml(productGroup),
                escapeXml(productType),
                weightKg,
                weightKg,
                pieces
        );
    }

    private String env(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property;
        }
        return System.getenv(key);
    }

    private String envOrDefault(String key, String fallback) {
        String value = env(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isBlank(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String summarizeStackTrace(Throwable error) {
        StackTraceElement[] stack = error.getStackTrace();
        int limit = Math.min(stack.length, 10);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            builder.append(stack[i]).append(i == limit - 1 ? "" : " | ");
        }
        return builder.toString();
    }
}
