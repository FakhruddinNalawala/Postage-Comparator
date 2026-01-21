package com.postage.postagecomparator.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AfterShipProviderTest {

    @Mock
    private QuoteRequestHelper requestHelper;

    @Mock
    private SettingsService settingsService;

    private AfterShipProvider buildProviderWithResponse(Map<String, Object> response) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(String.class))).thenReturn(uriSpec);
        when(uriSpec.contentType(any())).thenReturn(uriSpec);
        when(uriSpec.accept(any())).thenReturn(uriSpec);
        when(uriSpec.header(any(), any())).thenReturn(uriSpec);
        doReturn(headersSpec).when(uriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(response));

        return new AfterShipProvider(webClient, settingsService, requestHelper, new ObjectMapper());
    }

    private AfterShipProvider buildProviderWithError(Throwable error) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(String.class))).thenReturn(uriSpec);
        when(uriSpec.contentType(any())).thenReturn(uriSpec);
        when(uriSpec.accept(any())).thenReturn(uriSpec);
        when(uriSpec.header(any(), any())).thenReturn(uriSpec);
        doReturn(headersSpec).when(uriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(error));

        return new AfterShipProvider(webClient, settingsService, requestHelper, new ObjectMapper());
    }

    private AfterShipProvider buildProviderWithoutStubs() {
        return new AfterShipProvider(mock(WebClient.class), settingsService, requestHelper, new ObjectMapper());
    }

    @Test
    void quote_whenApiKeyMissing_returnsEmpty() {
        given(settingsService.getAfterShipApiKey()).willReturn(null);
        var provider = buildProviderWithoutStubs();

        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenValidResponse_returnsQuote() {
        Map<String, Object> response = Map.of(
                "data", Map.of(
                        "rates", List.of(
                                Map.of(
                                        "service_type", "Express",
                                        "total_charge", Map.of(
                                                "amount", 12.50,
                                                "currency", "AUD"
                                        )
                                )
                        )
                )
        );

        given(settingsService.getAfterShipApiKey()).willReturn("key");
        var provider = buildProviderWithResponse(response);
        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isPresent();
        assertThat(quote.get().totalCostAud()).isEqualTo(12.50);
        assertThat(quote.get().pricingSource()).isEqualTo("AFTERSHIP_API");
    }

    @Test
    void quote_whenWebClientException_returnsEmpty() {
        given(settingsService.getAfterShipApiKey()).willReturn("key");
        var provider = buildProviderWithError(new WebClientException("timeout") {});
        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenWebClientResponseException_returnsEmpty() {
        var exception = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                null,
                null
        );

        given(settingsService.getAfterShipApiKey()).willReturn("key");
        var provider = buildProviderWithError(exception);
        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }
}
