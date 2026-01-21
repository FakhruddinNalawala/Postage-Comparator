package com.postage.postagecomparator.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.model.ShipmentItemSelection;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import com.postage.postagecomparator.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AfterShip connector implementation of the CarrierProvider SPI.
 *
 * API docs: https://www.aftership.com/docs/shipping/f4353677b8ac1-calculate-rates
 */
@Component
public class AfterShipProvider implements CarrierProvider {

    private static final Logger log = LoggerFactory.getLogger(AfterShipProvider.class);

    private final WebClient afterShipWebClient;
    private final SettingsService settingsService;
    private final QuoteRequestHelper requestHelper;
    private final ObjectMapper objectMapper;

    public AfterShipProvider(@Qualifier("afterShipWebClient") WebClient afterShipWebClient,
                             SettingsService settingsService,
                             QuoteRequestHelper requestHelper,
                             ObjectMapper objectMapper) {
        this.afterShipWebClient = afterShipWebClient;
        this.settingsService = settingsService;
        this.requestHelper = requestHelper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "aftership";
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
        return quotes(request, origin, packaging, items)
                .flatMap(list -> list.stream().min(java.util.Comparator.comparingDouble(CarrierQuote::totalCostAud)));
    }

    @Override
    public Optional<List<CarrierQuote>> quotes(ShipmentRequest request,
                                               OriginSettings origin,
                                               Packaging packaging,
                                               List<Item> items) {
        String apiKey = settingsService.getAfterShipApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("AfterShip API key not configured, skipping API call and using rules-based pricing");
            return Optional.empty();
        }

        QuoteResult.Destination destination = requestHelper.buildDestination(request);
        int totalWeightGrams = requestHelper.calculateTotalWeight(request.items());
        int totalPieces = Math.max(1, request.items().stream()
                .mapToInt(ShipmentItemSelection::quantity)
                .sum());

        // AfterShip expects a shipment object containing ship_from, ship_to, and parcels.
        // TODO: confirm address and parcel schema details against AfterShip's model docs.
        Map<String, Object> shipFrom = Map.of(
                "city", nullToEmpty(origin.suburb()),
                "state", nullToEmpty(origin.state()),
                "postal_code", nullToEmpty(origin.postcode()),
                "country", nullToEmpty(origin.country())
        );

        Map<String, Object> shipTo = Map.of(
                "city", nullToEmpty(destination.suburb()),
                "state", nullToEmpty(destination.state()),
                "postal_code", nullToEmpty(destination.postcode()),
                "country", nullToEmpty(destination.country())
        );

        Map<String, Object> weight = Map.of(
                "value", Math.max(0.001, totalWeightGrams / 1000.0),
                "unit", "kg"
        );

        Map<String, Object> dimensions = Map.of(
                "unit", "cm",
                "length", packaging.lengthCm(),
                "width", packaging.widthCm(),
                "height", packaging.heightCm()
        );

        Map<String, Object> parcel = Map.of(
                "weight", weight,
                "dimensions", dimensions,
                "quantity", totalPieces
        );

        Map<String, Object> requestBody = Map.of(
                "ship_date", LocalDate.now().toString(),
                "shipment", Map.of(
                        "ship_from", shipFrom,
                        "ship_to", shipTo,
                        "parcels", List.of(parcel)
                )
        );

        try {
            String requestBodyJson = toJson(requestBody);
            log.info("Attempting AfterShip API call (POST /rates). Request: {}", requestBodyJson);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = afterShipWebClient
                    .post()
                    .uri("/rates")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("as-api-key", apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("AfterShip API returned 4xx (status: {}). Response: {}. Stack: {}",
                                        clientResponse.statusCode(),
                                        body,
                                        summarizeStackTrace(new RuntimeException("AfterShip API client error")));
                                return Mono.error(new RuntimeException(
                                        "AfterShip API client error: " + clientResponse.statusCode()));
                            }))
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("AfterShip API returned 5xx (status: {}). Response: {}. Stack: {}",
                                        clientResponse.statusCode(),
                                        body,
                                        summarizeStackTrace(new RuntimeException("AfterShip API server error")));
                                return Mono.error(new RuntimeException(
                                        "AfterShip API server error: " + clientResponse.statusCode()));
                            }))
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                log.warn("AfterShip API returned null response.");
                return Optional.empty();
            }

            List<CarrierQuote> quotes = parseAfterShipResponse(response, packaging);
            return Optional.ofNullable(quotes);
        } catch (WebClientResponseException e) {
            log.error("AfterShip API returned error response (status: {}). Response: {}. Stack: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    summarizeStackTrace(e));
            return Optional.empty();
        } catch (WebClientException e) {
            log.error("AfterShip API network/client error. Error: {}. Stack: {}",
                    e.getMessage(),
                    summarizeStackTrace(e));
            return Optional.empty();
        } catch (Exception e) {
            log.error("AfterShip API call failed with unexpected error. Error: {}. Stack: {}",
                    e.getMessage(),
                    summarizeStackTrace(e));
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<CarrierQuote> parseAfterShipResponse(Map<String, Object> response, Packaging packaging) {
        try {
            Object dataObj = response.get("data");
            Map<String, Object> data = dataObj instanceof Map<?, ?> map ? (Map<String, Object>) map : null;

            Object ratesObj = data != null ? data.get("rates") : response.get("rates");
            if (!(ratesObj instanceof List<?> list) || list.isEmpty()) {
                log.warn("AfterShip response missing rates array. Full response: {}", response);
                return List.of();
            }

            List<CarrierQuote> quotes = new ArrayList<>();
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> rateRaw)) {
                    continue;
                }
                Map<String, Object> rate = (Map<String, Object>) rateRaw;

                Money totalCharge = parseMoney(rate, "total_charge", "shipping_amount", "total_amount", "amount");
                if (totalCharge == null || totalCharge.amount() <= 0) {
                    continue;
                }
                if (totalCharge.currency() != null && !totalCharge.currency().equalsIgnoreCase("AUD")) {
                    continue;
                }

                String serviceName = firstString(rate, "service_type", "service_name", "service_level", "courier_name");
                Integer etaMin = null;
                Integer etaMax = null;

                double packagingCost = packaging.packagingCostAud();
                double deliveryCost = totalCharge.amount() - packagingCost;

                quotes.add(new CarrierQuote(
                        "AFTERSHIP",
                        serviceName != null ? serviceName : "rate",
                        etaMin,
                        etaMax,
                        packagingCost,
                        deliveryCost,
                        0.0,
                        totalCharge.amount(),
                        "AFTERSHIP_API",
                        false,
                        null
                ));
            }
            return quotes;
        } catch (Exception e) {
            log.error("Failed to parse AfterShip response: {}. Stack: {}", e.getMessage(), summarizeStackTrace(e));
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Money parseMoney(Map<String, Object> rate,
                             String... keys) {
        for (String key : keys) {
            Object value = rate.get(key);
            if (value instanceof Map<?, ?> moneyRaw) {
                String currency = firstString((Map<String, Object>) moneyRaw, "currency", "currency_code");
                Double amount = parseDouble(moneyRaw.get("amount"));
                if (amount != null) {
                    return new Money(currency, amount);
                }
            }
            Double amount = parseDouble(value);
            if (amount != null) {
                return new Money(null, amount);
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                String str = value.toString();
                if (!str.isBlank()) {
                    return str;
                }
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String summarizeStackTrace(Throwable t) {
        return Arrays.stream(t.getStackTrace())
                .limit(10)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" | "));
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AfterShip request to JSON: {}", e.getMessage());
            return String.valueOf(object);
        }
    }

    private record Money(String currency, double amount) {}
}
