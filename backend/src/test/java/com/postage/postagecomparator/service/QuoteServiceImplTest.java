package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceImplTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private ItemService itemService;

    @Mock
    private PackagingService packagingService;

    // We don't exercise HTTP clients in these unit tests; APIs are disabled via missing keys.
    private QuoteServiceImpl quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteServiceImpl(
                null, // ausPostWebClient (unused when API keys are missing)
                settingsService,
                itemService,
                packagingService
        );
    }

    // --- validateRequest via calculateQuote short-circuiting ---

    @Test
    void calculateQuote_whenRequestNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> quoteService.calculateQuote(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ShipmentRequest must not be null");

        verifyNoInteractions(settingsService, itemService, packagingService);
    }

    @Test
    void calculateQuote_whenDestinationPostcodeBlank_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "   ",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination postcode is required");

        verifyNoInteractions(settingsService, itemService, packagingService);
    }

    @Test
    void calculateQuote_whenItemsEmpty_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(),
                "pack-1",
                false
        );

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one item is required");

        verifyNoInteractions(settingsService, itemService, packagingService);
    }

    @Test
    void calculateQuote_whenPackagingIdBlank_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "   ",
                false
        );

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Packaging is required");

        verifyNoInteractions(settingsService, itemService, packagingService);
    }

    @Test
    void calculateQuote_whenItemQuantityNonPositive_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 0)),
                "pack-1",
                false
        );

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item quantity must be greater than 0");

        verifyNoInteractions(settingsService, itemService, packagingService);
    }

    // --- Origin / lookups / QuoteResult composition ---

    @Test
    void calculateQuote_whenOriginNotConfigured_throwsIllegalStateException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );

        given(settingsService.getOriginSettings()).willReturn(null);

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Origin settings must be configured");
    }

    @Test
    void calculateQuote_whenPackagingNotFound_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "missing-pack",
                false
        );

        given(settingsService.getOriginSettings())
                .willReturn(new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now()));
        given(packagingService.findById("missing-pack")).willReturn(Optional.empty());

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Packaging with id missing-pack not found");
    }

    @Test
    void calculateQuote_whenItemNotFound_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                "pack-1",
                false
        );

        given(settingsService.getOriginSettings())
                .willReturn(new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now()));
        given(packagingService.findById("pack-1"))
                .willReturn(Optional.of(new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 1.0)));
        given(itemService.findById("item-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item with id item-1 not found");
    }

    @Test
    void calculateQuote_whenApisDisabled_usesRulesBasedAusPostOnly() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                null, // country should default to AU in destination
                List.of(new ShipmentItemSelection("item-1", 2)),
                "pack-1",
                false
        );

        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Small box", null, 10, 10, 10, 1000, 2.0);
        var item = new Item("item-1", "Widget", null, 250);

        // 2 items * 250g = 500g
        given(settingsService.getOriginSettings()).willReturn(origin);
        given(packagingService.findById("pack-1")).willReturn(Optional.of(packaging));
        given(itemService.findById("item-1")).willReturn(Optional.of(item));

        // Disable external APIs so we exercise rules-based AusPost only.
        given(settingsService.getAusPostApiKey()).willReturn(null);

        // Provide brackets that will match the 0.5kg actual weight and the volume-weight (250 kg)
        var weightBracket = new WeightBracket(0.0, 1.0, 10.0, 15.0);
        var volumeBracket = new WeightBracket(200.0, 300.0, 20.0, 25.0);
        given(settingsService.getAusPostWeightBrackets())
                .willReturn(List.of(weightBracket, volumeBracket));

        QuoteResult result = quoteService.calculateQuote(request);

        assertThat(result.totalWeightGrams()).isEqualTo(500);
        assertThat(result.weightInKg()).isEqualTo(0.5);
        assertThat(result.origin()).isEqualTo(origin);
        assertThat(result.destination().postcode()).isEqualTo("3000");
        assertThat(result.destination().country()).isEqualTo("AU"); // defaulted
        assertThat(result.packaging()).isEqualTo(packaging);
        assertThat(result.carrierQuotes()).hasSize(1);

        var ausPostQuote = result.carrierQuotes().get(0);
        assertThat(ausPostQuote.carrier()).isEqualTo("AUSPOST");
        assertThat(ausPostQuote.pricingSource()).isEqualTo("RULES");
        assertThat(ausPostQuote.ruleFallbackUsed()).isTrue();
        // delivery cost is max of the two brackets' standard prices (20.0)
        assertThat(ausPostQuote.deliveryCostAud()).isEqualTo(20.0);
        assertThat(ausPostQuote.packagingCostAud()).isEqualTo(2.0);
        assertThat(ausPostQuote.totalCostAud()).isEqualTo(22.0);

        // Sendle integration disabled, so only AusPost is returned.
    }

    // --- Direct tests of AusPost rules-based pricing for edge cases ---

    @Test
    void calculateAusPostRulesBasedQuote_whenOnlyWeightMatches_usesWeightBracket() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 1.0);

        // 500g -> 0.5kg, volumeWeightInKg will be large (no volume bracket)
        int totalWeightGrams = 500;

        var weightBracket = new WeightBracket(0.0, 1.0, 10.0, 15.0);
        given(settingsService.getAusPostWeightBrackets())
                .willReturn(List.of(weightBracket));

        CarrierQuote quote = invokeAusPostRulesBased(origin, destination, totalWeightGrams, packaging, false);

        assertThat(quote.deliveryCostAud()).isEqualTo(10.0);
        assertThat(quote.totalCostAud()).isEqualTo(11.0);
    }

    @Test
    void calculateAusPostRulesBasedQuote_whenOnlyVolumeMatches_usesVolumeBracket() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 8000, 1.0);

        // 50g -> 0.05kg (no weight bracket), but volumeWeightInKg is very large and will
        // fall into the configured volume bracket.
        int totalWeightGrams = 50;

        var volumeBracket = new WeightBracket(1000.0, 2000.0, 20.0, 25.0);
        given(settingsService.getAusPostWeightBrackets())
                .willReturn(List.of(volumeBracket));

        CarrierQuote quote = invokeAusPostRulesBased(origin, destination, totalWeightGrams, packaging, false);

        assertThat(quote.deliveryCostAud()).isEqualTo(20.0);
        assertThat(quote.totalCostAud()).isEqualTo(21.0);
    }

    @Test
    void calculateAusPostRulesBasedQuote_whenNoBracketMatches_throwsIllegalArgumentException() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 1.0);

        int totalWeightGrams = 1000; // 1kg
        given(settingsService.getAusPostWeightBrackets())
                .willReturn(List.of(new WeightBracket(2.0, 3.0, 30.0, 35.0)));

        assertThatThrownBy(() -> invokeAusPostRulesBased(origin, destination, totalWeightGrams, packaging, false))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("No bracket found");
    }

    // --- AusPost API tests (Sendle disabled) ---

    @Test
    void tryAusPostApi_whenApiKeyPresent_andValidResponse_returnsQuote() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);

        Map<String, Object> response = Map.of(
                "postage_result", Map.of(
                        "total_cost", "12.50",
                        "service", "Parcel Post",
                        "delivery_time", "Delivered in 2-3 business days"
                )
        );

        var serviceWithWebClient = buildServiceWithAusPostResponse(response);
        given(settingsService.getAusPostApiKey()).willReturn("key");

        CarrierQuote quote = invokeTryAusPostApi(serviceWithWebClient, origin, destination, 500, packaging, false);

        assertThat(quote).isNotNull();
        assertThat(quote.carrier()).isEqualTo("AUSPOST");
        assertThat(quote.pricingSource()).isEqualTo("AUSPOST_API");
        assertThat(quote.deliveryEtaDaysMin()).isEqualTo(2);
        assertThat(quote.deliveryEtaDaysMax()).isEqualTo(3);
        assertThat(quote.totalCostAud()).isEqualTo(12.50);
        assertThat(quote.deliveryCostAud()).isEqualTo(10.50);
        assertThat(quote.packagingCostAud()).isEqualTo(2.0);
    }

    @Test
    void tryAusPostApi_whenResponseMissingPostageResult_returnsNull() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);

        var serviceWithWebClient = buildServiceWithAusPostResponse(Map.of());
        given(settingsService.getAusPostApiKey()).willReturn("key");

        CarrierQuote quote = invokeTryAusPostApi(serviceWithWebClient, origin, destination, 500, packaging, false);

        assertThat(quote).isNull();
    }

    @Test
    void tryAusPostApi_whenResponseNull_returnsNull() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);

        var serviceWithWebClient = buildServiceWithAusPostResponse(null);
        given(settingsService.getAusPostApiKey()).willReturn("key");

        CarrierQuote quote = invokeTryAusPostApi(serviceWithWebClient, origin, destination, 500, packaging, false);

        assertThat(quote).isNull();
    }

    @Test
    void tryAusPostApi_whenWebClientResponseException_returnsNull() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);

        var exception = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                null,
                null
        );
        var serviceWithWebClient = buildServiceWithAusPostError(exception);
        given(settingsService.getAusPostApiKey()).willReturn("key");

        CarrierQuote quote = invokeTryAusPostApi(serviceWithWebClient, origin, destination, 500, packaging, false);

        assertThat(quote).isNull();
    }

    @Test
    void tryAusPostApi_whenWebClientException_returnsNull() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);

        var serviceWithWebClient = buildServiceWithAusPostError(new WebClientException("timeout") {});
        given(settingsService.getAusPostApiKey()).willReturn("key");

        CarrierQuote quote = invokeTryAusPostApi(serviceWithWebClient, origin, destination, 500, packaging, false);

        assertThat(quote).isNull();
    }

    @Test
    void tryAusPostApi_whenRuntimeException_returnsNull() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);

        var serviceWithWebClient = buildServiceWithAusPostError(new RuntimeException("boom"));
        given(settingsService.getAusPostApiKey()).willReturn("key");

        CarrierQuote quote = invokeTryAusPostApi(serviceWithWebClient, origin, destination, 500, packaging, false);

        assertThat(quote).isNull();
    }

    @Test
    void parseDaysFromDeliveryTimeString_handlesRangeSingleAndInvalid() throws Exception {
        Method m = QuoteServiceImpl.class.getDeclaredMethod("parseDaysFromDeliveryTimeString", String.class);
        m.setAccessible(true);

        Integer[] range = (Integer[]) m.invoke(quoteService, "Delivered in 2-3 business days");
        Integer[] single = (Integer[]) m.invoke(quoteService, "Delivered in 4 business days");
        Integer[] invalid = (Integer[]) m.invoke(quoteService, "unknown");

        assertThat(range).containsExactly(2, 3);
        assertThat(single).containsExactly(4, 4);
        assertThat(invalid).isNull();
    }

    @Test
    void parseDouble_handlesNumberStringAndInvalid() throws Exception {
        Method m = QuoteServiceImpl.class.getDeclaredMethod("parseDouble", Object.class);
        m.setAccessible(true);

        Double num = (Double) m.invoke(quoteService, 2.5);
        Double str = (Double) m.invoke(quoteService, "3.75");
        Double bad = (Double) m.invoke(quoteService, "not-a-number");
        Double nullVal = (Double) m.invoke(quoteService, (Object) null);

        assertThat(num).isEqualTo(2.5);
        assertThat(str).isEqualTo(3.75);
        assertThat(bad).isNull();
        assertThat(nullVal).isNull();
    }

    // --- Helper to invoke private rules-based method via reflection ---

    private CarrierQuote invokeAusPostRulesBased(OriginSettings origin,
                                                 QuoteResult.Destination destination,
                                                 int totalWeightGrams,
                                                 Packaging packaging,
                                                 boolean isExpress) throws Exception {
        Method m = QuoteServiceImpl.class.getDeclaredMethod(
                "calculateAusPostRulesBasedQuote",
                OriginSettings.class,
                QuoteResult.Destination.class,
                int.class,
                Packaging.class,
                boolean.class
        );
        m.setAccessible(true);
        return (CarrierQuote) m.invoke(quoteService, origin, destination, totalWeightGrams, packaging, isExpress);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private QuoteServiceImpl buildServiceWithAusPostResponse(Map<String, Object> response) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(org.mockito.ArgumentMatchers.<Function<UriBuilder, URI>>any()))
                .thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString()))
                .thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(response == null ? Mono.empty() : Mono.just(response));

        return new QuoteServiceImpl(
                webClient,
                settingsService,
                itemService,
                packagingService
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private QuoteServiceImpl buildServiceWithAusPostError(Throwable error) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(org.mockito.ArgumentMatchers.<Function<UriBuilder, URI>>any()))
                .thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString()))
                .thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(error));

        return new QuoteServiceImpl(
                webClient,
                settingsService,
                itemService,
                packagingService
        );
    }

    private CarrierQuote invokeTryAusPostApi(QuoteServiceImpl service,
                                             OriginSettings origin,
                                             QuoteResult.Destination destination,
                                             int totalWeightGrams,
                                             Packaging packaging,
                                             boolean isExpress) throws Exception {
        Method m = QuoteServiceImpl.class.getDeclaredMethod(
                "tryAusPostApi",
                OriginSettings.class,
                QuoteResult.Destination.class,
                int.class,
                Packaging.class,
                boolean.class
        );
        m.setAccessible(true);
        return (CarrierQuote) m.invoke(service, origin, destination, totalWeightGrams, packaging, isExpress);
    }
}

