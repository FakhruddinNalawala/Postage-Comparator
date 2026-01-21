package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentItemSelection;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import com.postage.postagecomparator.service.SettingsService;

import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ShippitProvider implements CarrierProvider {

    // @SuppressWarnings("unused")
    private final WebClient shippitWebClient;
    private final SettingsService settingsService;
    private static final Logger log = LoggerFactory.getLogger(ShippitProvider.class);
    private final QuoteRequestHelper requestHelper;

    public ShippitProvider(@Qualifier("shippitWebClient") WebClient shippitWebClient, SettingsService settingsService, QuoteRequestHelper requestHelper) {
        this.shippitWebClient = shippitWebClient;
        this.settingsService = settingsService;
        this.requestHelper = requestHelper;
    }

    @Override
    public String getName() {
        return "shippit";
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
        Optional<List<CarrierQuote>> quotes = quotes(request, origin, packaging, items);
        return quotes.flatMap(list -> list.stream()
                .min(java.util.Comparator.comparingDouble(CarrierQuote::totalCostAud)));
    }

    @Override
    public Optional<List<CarrierQuote>> quotes(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items) {

        String apiKey = settingsService.getShippitApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Shippit API key not configured, skipping API call and using rules-based pricing");
            return Optional.empty(); // Return empty to allow fallback to rules
        }

        QuoteResult.Destination destination = requestHelper.buildDestination(request);
        int totalWeightGrams = requestHelper.calculateTotalWeight(request.items());
        boolean isExpress = request.isExpress();
        int totalQuantity = request.items().stream()
                .mapToInt(ShipmentItemSelection::quantity)
                .sum();
        double weightKg = totalWeightGrams / 1000.0;

        String uriShippitQuote = "/quotes";

        Map<String, Object> requestBody = Map.of(
                "quote", Map.of(
                        "dropoff_postcode", destination.postcode(),
                        "dropoff_state", destination.state(),
                        "dropoff_suburb", destination.suburb(),
                        "dropoff_country_code", destination.country(),
                        "parcel_attributes", List.of(Map.of(
                                "qty", totalQuantity,
                                "weight", weightKg,
                                "length", packaging.lengthCm() / 100.0,
                                "width", packaging.widthCm() / 100.0,
                                "depth", packaging.heightCm() / 100.0
                        )),
                        "service_levels", isExpress ? List.of("express") : List.of("standard"),
                        "return_all_quotes", true
                )
        );

        log.info("Attempting Shippit API call: from {} {} to {} {}, weight: {}g, service: {}",
                origin.postcode(), origin.suburb(),
                destination.postcode(), destination.suburb(),
                totalWeightGrams, isExpress ? "express" : "standard");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = shippitWebClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path(uriShippitQuote)
                            .build())
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> {
                                        log.error(
                                                "Shippit API returned 4xx client error (status: {}). Request: from {} {} to {} {}. Service: {}. Response: {}",
                                                clientResponse.statusCode(),
                                                origin.postcode(), origin.suburb(),
                                                destination.postcode(), destination.suburb(),
                                                isExpress ? "express" : "standard",
                                                body);
                                        return Mono.error(new RuntimeException(
                                                "Shippit API client error: " + clientResponse.statusCode()));
                                    }))
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> {
                                log.error(
                                        "Shippit API returned 5xx server error (status: {}). Request: from {} {} to {} {}. Service: {}",
                                        clientResponse.statusCode(),
                                        origin.postcode(), origin.suburb(),
                                        destination.postcode(), destination.suburb(),
                                        isExpress ? "express" : "standard");
                                return Mono.error(new RuntimeException(
                                        "Shippit API server error: " + clientResponse.statusCode()));
                            })
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.error("Shippit API returned null response. Request: from {} {} to {} {}. Service: {}",
                        origin.postcode(), origin.suburb(),
                        destination.postcode(), destination.suburb(),
                        isExpress ? "express" : "standard");
                return Optional.empty();
            }

            // Parse response and map to CarrierQuote
            // Shippit API response structure: typically has "postage_result" with cost,
            // service, etc.
            log.debug("Shippit API call succeeded, parsing response");
            List<CarrierQuote> quotes = parseShippitResponse(response, packaging, isExpress);
            if (quotes != null) {
                log.info("Shippit API quotes successfully retrieved: ${}", quotes.stream().map(CarrierQuote::totalCostAud).reduce(0.0, Double::sum));
            } else {
                log.warn("Shippit API response parsed to null, falling back to rules");
            }
            return Optional.ofNullable(quotes);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error(
                    "Shippit API returned error response (status: {}). Request: from {} {} to {} {}. Service: {}. Response: {}. Stack: {}",
                    e.getStatusCode(),
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    isExpress ? "express" : "standard",
                    e.getResponseBodyAsString(),
                    summarizeStackTrace(e));
            return Optional.empty();
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.error("Shippit API network/client error. Request: from {} {} to {} {}. Service: {}. Error: {}. Stack: {}",
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    isExpress ? "express" : "standard",
                    e.getMessage(),
                    summarizeStackTrace(e));
            return Optional.empty();
        } catch (RuntimeException e) {
            log.error(
                    "Shippit API call failed with runtime error. Request: from {} {} to {} {}. Service: {}. Error: {}. Stack: {}",
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    isExpress ? "express" : "standard",
                    e.getMessage(),
                    summarizeStackTrace(e));
            return Optional.empty();
        } catch (Exception e) {
            log.error(
                    "Shippit API call failed with unexpected error. Request: from {} {} to {} {}. Service: {}. Error: {}. Stack: {}",
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    isExpress ? "express" : "standard",
                    e.getMessage(),
                    summarizeStackTrace(e));
            return Optional.empty(); // Return null to allow fallback to rules
        }
    }

    @SuppressWarnings("unchecked")
    private List<CarrierQuote> parseShippitResponse(Map<String, Object> response, Packaging packaging, boolean isExpress) {
        try {
            log.debug("Parsing Shippit API response: {}", response);
            Object responseObj = response.get("response");
            if (!(responseObj instanceof List<?> responseList)) {
                log.error("Shippit response missing response array. Full response: {}", response);
                return List.of();
            }

            List<CarrierQuote> carrierQuotes = new ArrayList<>();
            String desiredService = isExpress ? "express" : "standard";

            for (Object entryObj : responseList) {
                if (!(entryObj instanceof Map<?, ?> entryMapRaw)) {
                    continue;
                }
                Map<String, Object> entry = (Map<String, Object>) entryMapRaw;
                Object successObj = entry.get("success");
                if (!(successObj instanceof Boolean success) || !success) {
                    continue;
                }

                String serviceLevel = extractString(entry, "service_level");
                if (serviceLevel != null && !serviceLevel.equalsIgnoreCase(desiredService)) {
                    continue;
                }

                String courierType = extractString(entry, "courier_type");
                Object quotesObj = entry.get("quotes");
                if (!(quotesObj instanceof List<?> quotesList) || quotesList.isEmpty()) {
                    continue;
                }

                for (Object quoteObj : quotesList) {
                    if (!(quoteObj instanceof Map<?, ?> quoteMapRaw)) {
                        continue;
                    }
                    Map<String, Object> quoteMap = (Map<String, Object>) quoteMapRaw;
                    Double totalCost = parseDouble(quoteMap.get("price"));
                    if (totalCost == null || totalCost <= 0) {
                        continue;
                    }

                    String quoteCourier = extractString(quoteMap, "courier_type");
                    String serviceName = quoteCourier != null ? quoteCourier
                            : (courierType != null ? courierType : desiredService);

                    String transitTime = extractString(quoteMap, "estimated_transit_time");
                    if (transitTime == null) {
                        transitTime = extractString(quoteMap, "estimated_delivery_time");
                    }

                    Integer etaMin = null;
                    Integer etaMax = null;
                    if (transitTime != null) {
                        Integer[] etaDays = parseDaysFromDeliveryTimeString(transitTime);
                        if (etaDays != null && etaDays.length == 2) {
                            etaMin = etaDays[0];
                            etaMax = etaDays[1];
                        } else if (etaDays != null && etaDays.length == 1) {
                            etaMin = etaDays[0];
                            etaMax = etaDays[0];
                        }
                    }

                    double packagingCostAud = packaging.packagingCostAud();
                    double deliveryCost = totalCost - packagingCostAud;
                    double surcharges = 0.0;

                    carrierQuotes.add(new CarrierQuote(
                            "SHIPPIT",
                            serviceName,
                            etaMin,
                            etaMax,
                            packagingCostAud,
                            deliveryCost,
                            surcharges,
                            totalCost,
                            "SHIPPIT_API",
                            false,
                            null
                    ));
                }
            }

            return carrierQuotes;
        } catch (ClassCastException e) {
            log.error("Failed to parse Shippit response: type casting error", e);
            return List.of();
        } catch (NullPointerException e) {
            log.error("Failed to parse Shippit response: null pointer exception", e);
            return List.of();
        } catch (NumberFormatException e) {
            log.error("Failed to parse Shippit response: number format error", e);
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse Shippit response: unexpected error", e);
            return List.of();
        }
    }

    /**
     * Parses the number of days from Shippit delivery time string.
     * Examples:
     * "Delivered in 4 business days" -> returns array with [4, 4]
     * "Delivered in 2-3 business days" -> returns array with [2, 3]
     * Returns null if parsing fails.
     */
    private Integer[] parseDaysFromDeliveryTimeString(String deliveryTimeStr) {
        if (deliveryTimeStr == null || deliveryTimeStr.isBlank()) {
            return null;
        }

        // First try to match range format: "Delivered in X-Y business days"
        java.util.regex.Pattern rangePattern = java.util.regex.Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
        java.util.regex.Matcher rangeMatcher = rangePattern.matcher(deliveryTimeStr);

        if (rangeMatcher.find()) {
            try {
                int min = Integer.parseInt(rangeMatcher.group(1));
                int max = Integer.parseInt(rangeMatcher.group(2));
                return new Integer[] { min, max };
            } catch (NumberFormatException e) {
                log.debug("Failed to parse range from delivery time string: {}", deliveryTimeStr);
            }
        }

        // Fallback to single number format: "Delivered in Z business days"
        java.util.regex.Pattern singlePattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher singleMatcher = singlePattern.matcher(deliveryTimeStr);

        if (singleMatcher.find()) {
            try {
                int days = Integer.parseInt(singleMatcher.group(1));
                return new Integer[] { days, days };
            } catch (NumberFormatException e) {
                log.debug("Failed to parse days from delivery time string: {}", deliveryTimeStr);
                return null;
            }
        }

        return null;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
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
