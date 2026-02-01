package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.*;
import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.config.PricingSourceFormatter;
import com.postage.postagecomparator.provider.CarrierProvider;
import com.postage.postagecomparator.provider.ProviderRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.postage.postagecomparator.util.DeliveryEtaUtils;

@Service
public class QuoteServiceImpl implements QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteServiceImpl.class);
    private static final String AUSPOST_PROVIDER_NAME = "auspost";
    private static final int STACK_TRACE_LIMIT = 10;

    // Sendle integration is currently disabled.
    // private final WebClient sendleWebClient;
    private final SettingsService settingsService;
    private final QuoteRequestHelper requestHelper;
    private final ProviderRegistry providerRegistry;
    private final ProviderConfig providerConfig;

    public QuoteServiceImpl(
            SettingsService settingsService,
            QuoteRequestHelper requestHelper,
            ProviderRegistry providerRegistry,
            ProviderConfig providerConfig) {
        this.settingsService = settingsService;
        this.requestHelper = requestHelper;
        this.providerRegistry = providerRegistry;
        this.providerConfig = providerConfig;
    }

    @Override
    public QuoteResult calculateQuote(ShipmentRequest request) {
        // Validate request
        requestHelper.validateRequest(request);

        // Get origin settings
        OriginSettings origin = requestHelper.getOriginSettingsOrThrow();

        // Calculate total item volume and find all packaging whose volume exceeds item volume
        int totalItemVolumeCubicCm = requestHelper.calculateTotalItemVolume(request.items());
        List<Packaging> fittingPackagings = requestHelper.findAllFittingPackaging(totalItemVolumeCubicCm);

        // Calculate total weight
        int totalWeightGrams = requestHelper.calculateTotalWeight(request.items());

        // Build destination
        QuoteResult.Destination destination = requestHelper.buildDestination(request);

        // Resolve items once for all providers
        List<Item> resolvedItems = requestHelper.resolveItems(request.items());

        // Get quotes for each fitting packaging and each express/standard option
        List<CarrierQuote> allCarrierQuotes = new ArrayList<>();
        for (Packaging packaging : fittingPackagings) {
            for (boolean isExpress : new boolean[]{false, true}) {
                List<CarrierQuote> quotes = getQuotesForPackaging(
                        request, origin, packaging, resolvedItems, destination, totalWeightGrams, isExpress);
                allCarrierQuotes.addAll(quotes);
            }
        }

        // Format pricing source for display
        List<CarrierQuote> displayQuotes = allCarrierQuotes.stream()
                .map(quote -> new CarrierQuote(
                        quote.carrier(),
                        quote.serviceName(),
                        quote.deliveryEtaDaysMin(),
                        quote.deliveryEtaDaysMax(),
                        quote.packagingCostAud(),
                        quote.deliveryCostAud(),
                        quote.surchargesAud(),
                        quote.totalCostAud(),
                        PricingSourceFormatter.format(quote.pricingSource()),
                        quote.ruleFallbackUsed(),
                        quote.rawCarrierRef(),
                        quote.packagingName(),
                        quote.isExpress()))
                .toList();

        // Use smallest fitting packaging as primary (best for volume-weight carriers)
        Packaging primaryPackaging = fittingPackagings.stream()
                .min((p1, p2) -> Integer.compare(p1.volumeCubicCm(), p2.volumeCubicCm()))
                .orElseThrow();
        double volumeWeightInKg = primaryPackaging.volumeCubicCm() * 0.25 / 1000.0;

        // For reporting, expose the total *item* volume, not the box volume.
        int totalVolumeCubicCm = totalItemVolumeCubicCm;

        return new QuoteResult(
                totalWeightGrams,
                totalWeightGrams / 1000.0,
                volumeWeightInKg,
                totalVolumeCubicCm,
                origin,
                destination,
                primaryPackaging,
                displayQuotes,
                "AUD",
                Instant.now());
    }

    /**
     * Get quotes from all providers for a specific packaging option and delivery speed.
     */
    private List<CarrierQuote> getQuotesForPackaging(
            ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> resolvedItems,
            QuoteResult.Destination destination,
            int totalWeightGrams,
            boolean isExpress) {
        
        List<CarrierQuote> carrierQuotes = new ArrayList<>();
        boolean ausPostProvidedQuote = false;
        boolean ausPostOnly = request.isAusPostOnly();
        
        if (ausPostOnly) {
            log.info("AusPost-only mode enabled (PO Box / Parcel Locker destination)");
        }

        for (CarrierProvider provider : providerRegistry.getEnabledProviders(providerConfig)) {
            String providerName = provider.getName();
            
            // Skip non-AusPost providers if ausPostOnly is set
            if (ausPostOnly && !AUSPOST_PROVIDER_NAME.equalsIgnoreCase(providerName)) {
                log.debug("Skipping provider '{}' - AusPost-only mode enabled", providerName);
                continue;
            }
            
            try {
                Optional<List<CarrierQuote>> multiQuotes = provider.quotes(request, origin, packaging, resolvedItems, isExpress)
                        .filter(quotes -> !quotes.isEmpty());
                if (multiQuotes.isPresent()) {
                    // Add packaging name and isExpress to each quote
                    carrierQuotes.addAll(multiQuotes.get().stream()
                            .map(q -> withPackagingNameAndExpress(q, packaging.name(), isExpress))
                            .toList());
                    if (AUSPOST_PROVIDER_NAME.equalsIgnoreCase(providerName)) {
                        ausPostProvidedQuote = true;
                    }
                    continue;
                }

                Optional<CarrierQuote> singleQuote = provider.quote(request, origin, packaging, resolvedItems, isExpress);
                if (singleQuote.isPresent()) {
                    carrierQuotes.add(withPackagingNameAndExpress(singleQuote.get(), packaging.name(), isExpress));
                    if (AUSPOST_PROVIDER_NAME.equalsIgnoreCase(providerName)) {
                        ausPostProvidedQuote = true;
                    }
                }
            } catch (RuntimeException e) {
                log.error("Provider '{}' failed during {} quote with packaging '{}'; continuing. Stack: {}",
                        providerName,
                        isExpress ? "express" : "standard",
                        packaging.name(),
                        summarizeStackTrace(e));
            }
        }

        if (!ausPostProvidedQuote) {
            CarrierQuote ausPostQuote = calculateAusPostRulesBasedQuote(
                    origin, destination, totalWeightGrams, packaging, isExpress);
            carrierQuotes.add(withPackagingNameAndExpress(ausPostQuote, packaging.name(), isExpress));
        }

        return carrierQuotes;
    }

    /**
     * Create a new CarrierQuote with the packaging name and isExpress set.
     */
    private CarrierQuote withPackagingNameAndExpress(CarrierQuote quote, String packagingName, boolean isExpress) {
        return new CarrierQuote(
                quote.carrier(),
                quote.serviceName(),
                quote.deliveryEtaDaysMin(),
                quote.deliveryEtaDaysMax(),
                quote.packagingCostAud(),
                quote.deliveryCostAud(),
                quote.surchargesAud(),
                quote.totalCostAud(),
                quote.pricingSource(),
                quote.ruleFallbackUsed(),
                quote.rawCarrierRef(),
                packagingName,
                isExpress);
    }

    private CarrierQuote calculateAusPostRulesBasedQuote(
            OriginSettings origin,
            QuoteResult.Destination destination,
            int totalWeightGrams,
            Packaging packaging,
            boolean isExpress) {

        /*
         * | Weight Range | Parcel Post (standard) | Express Post (faster) |
         * | ------------ | ---------------------- | --------------------- |
         * | Up to 250 g | $9.70 | $12.70 |
         * | 250 g–500 g | $11.15 | $14.65 |
         * | 500 g–1 kg | $15.25 | $19.25 |
         * | 1 kg–3 kg | $19.30 | $23.80 |
         * | 3 kg–5 kg | $23.30 | $31.80 |
         */

        // Placeholder implementation
        log.debug("Calculating rules-based quote for carrier: AUSPOST, weight: {}g, volume: {}cm³",
                totalWeightGrams, packaging.volumeCubicCm());

        double weightInKg = totalWeightGrams / 1000.0;
        double volumeWeightInKg = packaging.volumeCubicCm() * 0.25 / 1000.0;
        List<WeightBracket> brackets = settingsService.getAusPostWeightBrackets();
        Optional<WeightBracket> weightBracket = findBracket(brackets, weightInKg);
        Optional<WeightBracket> volumeBracket = findBracket(brackets, volumeWeightInKg);

        if (weightBracket.isEmpty() && volumeBracket.isEmpty()) {
            throw new IllegalArgumentException("No bracket found");
        }

        double deliveryCost = isExpress
                ? Stream.of(weightBracket, volumeBracket)
                        .flatMap(Optional::stream)
                        .mapToDouble(WeightBracket::priceExpress)
                        .max()
                        .orElseThrow()
                : Stream.of(weightBracket, volumeBracket)
                        .flatMap(Optional::stream)
                        .mapToDouble(WeightBracket::priceStandard)
                        .max()
                        .orElseThrow();
        double packagingCostAud = packaging.packagingCostAud();
        double totalCost = packagingCostAud + deliveryCost;

        DeliveryEtaUtils.EtaResult etaResult = DeliveryEtaUtils.calculateEta(origin.postcode(), destination.postcode(),
                origin.state(), destination.state(), isExpress);

        return new CarrierQuote(
                "AUSPOST",
                isExpress ? "Express Post (Rules)" : "Parcel Post (Rules)",
                etaResult.minDays(),
                etaResult.maxDays(),
                packaging.packagingCostAud(),
                deliveryCost,
                0.0,
                totalCost,
                "RULES",
                true, // ruleFallbackUsed
                null, // rawCarrierRef
                null, // packagingName - set by withPackagingNameAndExpress()
                false // isExpress - set by withPackagingNameAndExpress()
        );
    }

    /*
    private CarrierQuote trySendleApi(OriginSettings origin, QuoteResult.Destination destination, int totalWeightGrams,
            Packaging packaging, boolean isExpress) {
        String apiKey = settingsService.getSendleApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null; // Return null to allow fallback to rules
        }

        try {
            String sendleId = settingsService.getSendleId();
            String sendleApiKey = settingsService.getSendleApiKey();
            if (sendleId == null || sendleId.isBlank() || sendleApiKey == null || sendleApiKey.isBlank()) {
                return null; // Return null to allow fallback to rules
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> response = sendleWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/products")
                            .queryParam("weight_units", "kg")
                            .queryParam("dimension_units", "cm")
                            .queryParam("sender_suburb", origin.suburb())
                            .queryParam("sender_postcode", origin.postcode())
                            .queryParam("sender_country", origin.country())
                            .queryParam("receiver_suburb", destination.suburb())
                            .queryParam("receiver_postcode", destination.postcode())
                            .queryParam("receiver_country", destination.country())
                            .queryParam("weight_value", totalWeightGrams / 1000.0)
                            .queryParam("length_value", packaging.lengthCm())
                            .queryParam("width_value", packaging.widthCm())
                            .queryParam("height_value", packaging.heightCm())
                            .queryParam("packaging_type", "box")
                            .queryParam("total_cover_amount", 0)
                            .build())
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((sendleId + ":" + sendleApiKey)
                                    .getBytes(StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Sleepsake_Postage_Comparator/1.0")
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            clientResponse -> {
                                log.warn("Sendle API returned non-200 status: {}", clientResponse.statusCode());
                                return Mono.error(new RuntimeException("Sendle API returned status: " + clientResponse.statusCode()));
                            })
                    .bodyToMono(List.class)
                    .block();

            if (response == null || response.isEmpty()) {
                log.warn("Sendle API returned null or empty response");
                return null;
            }

            CarrierQuote quote = parseSendleResponse(response, packaging, isExpress);
            return quote;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("Sendle API returned error response (status: {}). Request: from {} {} to {} {}. Error: {}", 
                    e.getStatusCode(), 
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    e.getMessage(), e);
            return null;
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.error("Sendle API network/client error. Request: from {} {} to {} {}. Error: {}", 
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    e.getMessage(), e);
            return null;
        } catch (RuntimeException e) {
            log.error("Sendle API call failed with runtime error. Request: from {} {} to {} {}. Error: {}", 
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("Sendle API call failed with unexpected error. Request: from {} {} to {} {}. Error: {}", 
                    origin.postcode(), origin.suburb(),
                    destination.postcode(), destination.suburb(),
                    e.getMessage(), e);
            return null; // Return null to allow fallback to rules
        }
    }

    @SuppressWarnings("unchecked")
    private CarrierQuote parseSendleResponse(List<Map<String, Object>> response, Packaging packaging,
            boolean isExpress) {
        try {
            if (response == null || response.isEmpty()) {
                log.warn("Sendle response is null or empty");
                return null;
            }

            // Find the product that matches the requested service type (express vs standard)
            Map<String, Object> selectedProduct = response.stream()
                    .filter(product -> {
                        Map<String, Object> productInfo = (Map<String, Object>) product.get("product");
                        if (productInfo == null) {
                            return false;
                        }
                        String service = extractString(productInfo, "service");
                        String firstMileOption = extractString(productInfo, "first_mile_option");
                        // Match express/standard service type
                        return (isExpress && "express".equalsIgnoreCase(service)) ||
                                ((!isExpress && "standard".equalsIgnoreCase(service))
                                        && firstMileOption != null
                                        && firstMileOption.equalsIgnoreCase("drop off"));
                    })
                    .findFirst()
                    .orElseGet(() -> {
                        log.debug("No exact service match found, using first product");
                        return response.get(0);
                    });

            // Extract quote information
            Map<String, Object> quote = (Map<String, Object>) selectedProduct.get("quote");
            if (quote == null) {
                log.warn("Sendle response missing quote");
                return null;
            }

            Map<String, Object> gross = (Map<String, Object>) quote.get("gross");
            Double totalCost = gross != null ? parseDouble(gross.get("amount")) : null;
            if (totalCost == null) {
                log.warn("Sendle response missing quote.gross.amount");
                return null;
            }
            totalCost = totalCost + packaging.packagingCostAud();

            // Extract ETA days_range [min, max]
            Map<String, Object> eta = (Map<String, Object>) selectedProduct.get("eta");
            Integer etaMin = null;
            Integer etaMax = null;
            if (eta != null) {
                List<Integer> daysRange = (List<Integer>) eta.get("days_range");
                if (daysRange != null && daysRange.size() >= 2) {
                    etaMin = daysRange.get(0);
                    etaMax = daysRange.get(1);
                }
            }

            // Extract service name
            Map<String, Object> productInfo = (Map<String, Object>) selectedProduct.get("product");
            String serviceName = productInfo != null ? extractString(productInfo, "name") : null;
            if (serviceName == null) {
                serviceName = "Standard";
            }

            // Extract surcharges from price_breakdown
            Map<String, Object> priceBreakdown = (Map<String, Object>) selectedProduct.get("price_breakdown");
            double surcharges = 0.0;
            if (priceBreakdown != null) {
                Map<String, Object> fuelSurcharge = (Map<String, Object>) priceBreakdown.get("fuel_surcharge");
                if (fuelSurcharge != null) {
                    Double fuelSurchargeAmount = parseDouble(fuelSurcharge.get("amount"));
                    if (fuelSurchargeAmount != null) {
                        surcharges += fuelSurchargeAmount;
                    }
                }
                Map<String, Object> service = (Map<String, Object>) priceBreakdown.get("service");
                if (service != null) {
                    Double serviceAmount = parseDouble(service.get("amount"));
                    if (serviceAmount != null) {
                        surcharges += serviceAmount;
                    }
                }
            }

            // Calculate delivery cost (total cost minus packaging cost)
            double packagingCostAud = packaging.packagingCostAud();
            double deliveryCost = totalCost - packagingCostAud - surcharges;

            // Extract product code for rawCarrierRef
            String productCode = productInfo != null ? extractString(productInfo, "code") : null;

            return new CarrierQuote(
                    "SENDLE",
                    serviceName,
                    etaMin != null ? etaMin : (isExpress ? 1 : 2),
                    etaMax != null ? etaMax : (isExpress ? 3 : 6),
                    packagingCostAud,
                    deliveryCost,
                    surcharges,
                    totalCost,
                    "SENDLE_API",
                    false, // ruleFallbackUsed
                    productCode, // rawCarrierRef
                    null, // packagingName - set by caller
                    false // isExpress - set by caller
            );
        } catch (ClassCastException e) {
            log.error("Failed to parse Sendle response: type casting error", e);
            return null;
        } catch (NullPointerException e) {
            log.error("Failed to parse Sendle response: null pointer exception", e);
            return null;
        } catch (NumberFormatException e) {
            log.error("Failed to parse Sendle response: number format error", e);
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Sendle response: unexpected error", e);
            return null;
        }
    }
    */

    // @SuppressWarnings("unused")
    // private CarrierQuote tryAusPostApi(OriginSettings origin, QuoteResult.Destination destination, int totalWeightGrams,
    //         Packaging packaging, boolean isExpress) {

    //     String apiKey = settingsService.getAusPostApiKey();
    //     if (apiKey == null || apiKey.isBlank()) {
    //         log.info("AusPost API key not configured, skipping API call and using rules-based pricing");
    //         return null; // Return null to allow fallback to rules
    //     }
    //     String uriAusPostCalculate = "/postage/parcel/domestic/calculate.json";

    //     String serviceCode = isExpress ? "AUS_PARCEL_EXPRESS" : "AUS_PARCEL_REGULAR";

    //     log.info("Attempting AusPost API call: from {} {} to {} {}, weight: {}g, service: {}", 
    //             origin.postcode(), origin.suburb(),
    //             destination.postcode(), destination.suburb(),
    //             totalWeightGrams, serviceCode);

    //     try {
    //         @SuppressWarnings("unchecked")
    //         Map<String, Object> response = ausPostWebClient
    //                 .get()
    //                 .uri(uriBuilder -> uriBuilder
    //                         .path(uriAusPostCalculate)
    //                         .queryParam("from_postcode", origin.postcode())
    //                         .queryParam("to_postcode", destination.postcode())
    //                         .queryParam("length", String.valueOf(packaging.lengthCm()))
    //                         .queryParam("width", String.valueOf(packaging.widthCm()))
    //                         .queryParam("height", String.valueOf(packaging.heightCm()))
    //                         .queryParam("weight", String.valueOf(totalWeightGrams / 1000.0))
    //                         .queryParam("service_code", serviceCode)
    //                         .build())
    //                 .header("AUTH-KEY", apiKey)
    //                 .retrieve()
    //                 .onStatus(
    //                         status -> status.is4xxClientError(),
    //                         clientResponse -> {
    //                             log.error("AusPost API returned 4xx client error (status: {}). Request: from {} {} to {} {}. Service: {}", 
    //                                     clientResponse.statusCode(),
    //                                     origin.postcode(), origin.suburb(),
    //                                     destination.postcode(), destination.suburb(),
    //                                     serviceCode);
    //                             return Mono.error(new RuntimeException("AusPost API client error: " + clientResponse.statusCode()));
    //                         })
    //                 .onStatus(
    //                         status -> status.is5xxServerError(),
    //                         clientResponse -> {
    //                             log.error("AusPost API returned 5xx server error (status: {}). Request: from {} {} to {} {}. Service: {}", 
    //                                     clientResponse.statusCode(),
    //                                     origin.postcode(), origin.suburb(),
    //                                     destination.postcode(), destination.suburb(),
    //                                     serviceCode);
    //                             return Mono.error(new RuntimeException("AusPost API server error: " + clientResponse.statusCode()));
    //                         })
    //                 .bodyToMono(Map.class)
    //                 .block();

    //         if (response == null) {
    //             log.error("AusPost API returned null response. Request: from {} {} to {} {}. Service: {}", 
    //                     origin.postcode(), origin.suburb(),
    //                     destination.postcode(), destination.suburb(),
    //                     serviceCode);
    //             return null;
    //         }

    //         // Parse response and map to CarrierQuote
    //         // AusPost API response structure: typically has "postage_result" with cost,
    //         // service, etc.
    //         log.debug("AusPost API call succeeded, parsing response");
    //         CarrierQuote quote = parseAusPostResponse(response, packaging, isExpress);
    //         if (quote != null) {
    //             log.info("AusPost API quote successfully retrieved: ${}", quote.totalCostAud());
    //         } else {
    //             log.warn("AusPost API response parsed to null, falling back to rules");
    //         }
    //         return quote;
    //     } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
    //         log.error("AusPost API returned error response (status: {}). Request: from {} {} to {} {}. Service: {}. Error: {}", 
    //                 e.getStatusCode(),
    //                 origin.postcode(), origin.suburb(),
    //                 destination.postcode(), destination.suburb(),
    //                 serviceCode,
    //                 e.getMessage(), e);
    //         return null;
    //     } catch (org.springframework.web.reactive.function.client.WebClientException e) {
    //         log.error("AusPost API network/client error. Request: from {} {} to {} {}. Service: {}. Error: {}", 
    //                 origin.postcode(), origin.suburb(),
    //                 destination.postcode(), destination.suburb(),
    //                 serviceCode,
    //                 e.getMessage(), e);
    //         return null;
    //     } catch (RuntimeException e) {
    //         log.error("AusPost API call failed with runtime error. Request: from {} {} to {} {}. Service: {}. Error: {}", 
    //                 origin.postcode(), origin.suburb(),
    //                 destination.postcode(), destination.suburb(),
    //                 serviceCode,
    //                 e.getMessage(), e);
    //         return null;
    //     } catch (Exception e) {
    //         log.error("AusPost API call failed with unexpected error. Request: from {} {} to {} {}. Service: {}. Error: {}", 
    //                 origin.postcode(), origin.suburb(),
    //                 destination.postcode(), destination.suburb(),
    //                 serviceCode,
    //                 e.getMessage(), e);
    //         return null; // Return null to allow fallback to rules
    //     }
    // }

    // @SuppressWarnings("unchecked")
    // private CarrierQuote parseAusPostResponse(Map<String, Object> response, Packaging packaging, boolean isExpress) {
    //     try {
    //         log.debug("Parsing AusPost API response: {}", response);
    //         Map<String, Object> postageResult = (Map<String, Object>) response.get("postage_result");
    //         if (postageResult == null) {
    //             log.error("AusPost response missing postage_result. Full response: {}", response);
    //             return null;
    //         }

    //         // Extract cost from "total_cost" (string format like "15.05")
    //         Object costObj = postageResult.get("total_cost");
    //         Double totalCost = costObj != null ? parseDouble(costObj) : null;

    //         if (totalCost == null) {
    //             log.error("AusPost response missing total_cost. postage_result: {}", postageResult);
    //             return null;
    //         }

    //         // Extract service name
    //         String serviceName = extractString(postageResult, "service");
    //         if (serviceName == null) {
    //             serviceName = isExpress ? "Express Post" : "Parcel Post";
    //         }

    //         // Extract delivery time from string format like "Delivered in 4 business days"
    //         // Try to parse the number(s) from the string
    //         String deliveryTimeStr = extractString(postageResult, "delivery_time");
    //         Integer etaMin = null;
    //         Integer etaMax = null;
    //         if (deliveryTimeStr != null) {
    //             Integer[] etaDays = parseDaysFromDeliveryTimeString(deliveryTimeStr);
    //             if (etaDays != null && etaDays.length == 2) {
    //                 etaMin = etaDays[0];
    //                 etaMax = etaDays[1];
    //             } else if (etaDays != null && etaDays.length == 1) {
    //                 etaMin = etaDays[0];
    //                 etaMax = etaDays[0];
    //             }
    //         }

    //         // Calculate delivery cost (total cost minus packaging cost)
    //         double packagingCostAud = packaging.packagingCostAud();
    //         double deliveryCost = totalCost - packagingCostAud;
    //         double surcharges = 0.0; // AusPost may include surcharges in total_cost or separately

    //         return new CarrierQuote(
    //                 "AUSPOST",
    //                 serviceName,
    //                 etaMin != null ? etaMin : (isExpress ? 1 : 2),
    //                 etaMax != null ? etaMax : (isExpress ? 3 : 6),
    //                 packagingCostAud,
    //                 deliveryCost,
    //                 surcharges,
    //                 totalCost,
    //                 "AUSPOST_API",
    //                 false, // ruleFallbackUsed
    //                 null // rawCarrierRef - could extract from response if available
    //         );
    //     } catch (ClassCastException e) {
    //         log.error("Failed to parse AusPost response: type casting error", e);
    //         return null;
    //     } catch (NullPointerException e) {
    //         log.error("Failed to parse AusPost response: null pointer exception", e);
    //         return null;
    //     } catch (NumberFormatException e) {
    //         log.error("Failed to parse AusPost response: number format error", e);
    //         return null;
    //     } catch (Exception e) {
    //         log.error("Failed to parse AusPost response: unexpected error", e);
    //         return null;
    //     }
    // }

    /**
     * Parses the number of days from AusPost delivery time string.
     * Examples: 
     * "Delivered in 4 business days" -> returns array with [4, 4]
     * "Delivered in 2-3 business days" -> returns array with [2, 3]
     * Returns null if parsing fails.
     */
    // private Integer[] parseDaysFromDeliveryTimeString(String deliveryTimeStr) {
    //     if (deliveryTimeStr == null || deliveryTimeStr.isBlank()) {
    //         return null;
    //     }

    //     // First try to match range format: "Delivered in X-Y business days"
    //     java.util.regex.Pattern rangePattern = java.util.regex.Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
    //     java.util.regex.Matcher rangeMatcher = rangePattern.matcher(deliveryTimeStr);

    //     if (rangeMatcher.find()) {
    //         try {
    //             int min = Integer.parseInt(rangeMatcher.group(1));
    //             int max = Integer.parseInt(rangeMatcher.group(2));
    //             return new Integer[]{min, max};
    //         } catch (NumberFormatException e) {
    //             log.debug("Failed to parse range from delivery time string: {}", deliveryTimeStr);
    //         }
    //     }

    //     // Fallback to single number format: "Delivered in Z business days"
    //     java.util.regex.Pattern singlePattern = java.util.regex.Pattern.compile("(\\d+)");
    //     java.util.regex.Matcher singleMatcher = singlePattern.matcher(deliveryTimeStr);

    //     if (singleMatcher.find()) {
    //         try {
    //             int days = Integer.parseInt(singleMatcher.group(1));
    //             return new Integer[]{days, days};
    //         } catch (NumberFormatException e) {
    //             log.debug("Failed to parse days from delivery time string: {}", deliveryTimeStr);
    //             return null;
    //         }
    //     }

    //     return null;
    // }

    // private Double parseDouble(Object value) {
    //     if (value == null) {
    //         return null;
    //     }
    //     if (value instanceof Number number) {
    //         return number.doubleValue();
    //     }
    //     if (value instanceof String str) {
    //         try {
    //             return Double.parseDouble(str);
    //         } catch (NumberFormatException e) {
    //             return null;
    //         }
    //     }
    //     return null;
    // }

    // private String extractString(Map<String, Object> map, String key) {
    //     Object value = map.get(key);
    //     return value != null ? value.toString() : null;
    // }

    private String summarizeStackTrace(Throwable error) {
        return Arrays.stream(error.getStackTrace())
                .limit(STACK_TRACE_LIMIT)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" | "));
    }

    private Optional<WeightBracket> findBracket(List<WeightBracket> brackets, double weightInKg) {
        return brackets.stream()
                .filter(bracket -> weightInKg > bracket.minWeightInclusive()
                        && weightInKg <= bracket.maxWeightInclusive())
                .findFirst();
    }
}
