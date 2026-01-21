package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import com.postage.postagecomparator.service.SettingsService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AusPostProviderTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private QuoteRequestHelper requestHelper;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private AusPostProvider buildProviderWithResponse(Map<String, Object> response) {
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

        return new AusPostProvider(webClient, settingsService, requestHelper);
    }

    private AusPostProvider buildProviderWithoutStubs() {
        return new AusPostProvider(mock(WebClient.class), settingsService, requestHelper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private AusPostProvider buildProviderWithError(Throwable error) {
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

        return new AusPostProvider(webClient, settingsService, requestHelper);
    }

    @Test
    void quote_whenApiKeyMissing_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithoutStubs();
        given(settingsService.getAusPostApiKey()).willReturn("  ");

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenApiKeyPresent_andValidResponse_returnsQuote() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        Map<String, Object> response = Map.of(
                "postage_result", Map.of(
                        "total_cost", "12.50",
                        "service", "Parcel Post",
                        "delivery_time", "Delivered in 2-3 business days"
                )
        );

        var provider = buildProviderWithResponse(response);
        given(settingsService.getAusPostApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isPresent();
        assertThat(quote.get().carrier()).isEqualTo("AUSPOST");
        assertThat(quote.get().pricingSource()).isEqualTo("AUSPOST_API");
        assertThat(quote.get().deliveryEtaDaysMin()).isEqualTo(2);
        assertThat(quote.get().deliveryEtaDaysMax()).isEqualTo(3);
        assertThat(quote.get().totalCostAud()).isEqualTo(12.50);
        assertThat(quote.get().deliveryCostAud()).isEqualTo(10.50);
        assertThat(quote.get().packagingCostAud()).isEqualTo(2.0);
    }

    @Test
    void quote_whenResponseMissingPostageResult_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithResponse(Map.of());
        given(settingsService.getAusPostApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenResponseNull_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithResponse(null);
        given(settingsService.getAusPostApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenWebClientResponseException_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var exception = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                null,
                null
        );
        var provider = buildProviderWithError(exception);
        given(settingsService.getAusPostApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenWebClientException_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithError(new WebClientException("timeout") {});
        given(settingsService.getAusPostApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenRuntimeException_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithError(new RuntimeException("boom"));
        given(settingsService.getAusPostApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void parseDaysFromDeliveryTimeString_handlesRangeSingleAndInvalid() throws Exception {
        var provider = buildProviderWithoutStubs();
        Method m = AusPostProvider.class.getDeclaredMethod("parseDaysFromDeliveryTimeString", String.class);
        m.setAccessible(true);

        Integer[] range = (Integer[]) m.invoke(provider, "Delivered in 2-3 business days");
        Integer[] single = (Integer[]) m.invoke(provider, "Delivered in 4 business days");
        Integer[] invalid = (Integer[]) m.invoke(provider, "unknown");

        assertThat(range).containsExactly(2, 3);
        assertThat(single).containsExactly(4, 4);
        assertThat(invalid).isNull();
    }

    @Test
    void parseDouble_handlesNumberStringAndInvalid() throws Exception {
        var provider = buildProviderWithoutStubs();
        Method m = AusPostProvider.class.getDeclaredMethod("parseDouble", Object.class);
        m.setAccessible(true);

        Double num = (Double) m.invoke(provider, 2.5);
        Double str = (Double) m.invoke(provider, "3.75");
        Double bad = (Double) m.invoke(provider, "not-a-number");
        Double nullVal = (Double) m.invoke(provider, (Object) null);

        assertThat(num).isEqualTo(2.5);
        assertThat(str).isEqualTo(3.75);
        assertThat(bad).isNull();
        assertThat(nullVal).isNull();
    }

    @Test
    void isEnabled_readsProviderConfig() {
        var provider = buildProviderWithoutStubs();
        var config = new com.postage.postagecomparator.config.ProviderConfig();
        config.setProviders(Map.of(
                "auspost",
                new com.postage.postagecomparator.config.ProviderConfig.ProviderSettings("key", null, true)
        ));

        assertThat(provider.isEnabled(config)).isTrue();
    }
}
