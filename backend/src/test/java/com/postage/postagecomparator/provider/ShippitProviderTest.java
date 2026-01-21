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
class ShippitProviderTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private QuoteRequestHelper requestHelper;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ShippitProvider buildProviderWithResponse(Map<String, Object> response) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(org.mockito.ArgumentMatchers.<Function<UriBuilder, URI>>any()))
                .thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString()))
                .thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.accept(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(response == null ? Mono.empty() : Mono.just(response));

        return new ShippitProvider(webClient, settingsService, requestHelper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ShippitProvider buildProviderWithError(Throwable error) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(org.mockito.ArgumentMatchers.<Function<UriBuilder, URI>>any()))
                .thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString()))
                .thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.accept(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(error));

        return new ShippitProvider(webClient, settingsService, requestHelper);
    }

    private ShippitProvider buildProviderWithoutStubs() {
        return new ShippitProvider(mock(WebClient.class), settingsService, requestHelper);
    }

    @Test
    void quotes_whenApiKeyMissing_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithoutStubs();
        given(settingsService.getShippitApiKey()).willReturn(" ");

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isEmpty();
    }

    @Test
    void quotes_whenExpress_filtersServiceLevel() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", true);

        Map<String, Object> response = Map.of(
                "response", List.of(
                        Map.of(
                                "courier_type", "eParcelExpress",
                                "service_level", "express",
                                "success", true,
                                "quotes", List.of(
                                        Map.of("price", 30.4, "estimated_transit_time", "1 business day")
                                )
                        ),
                        Map.of(
                                "courier_type", "CouriersPlease",
                                "service_level", "standard",
                                "success", true,
                                "quotes", List.of(
                                        Map.of("price", 20.4, "estimated_transit_time", "3 business days")
                                )
                        )
                ),
                "count", 2
        );

        var provider = buildProviderWithResponse(response);
        given(settingsService.getShippitApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isPresent();
        assertThat(quotes.get()).hasSize(1);
        assertThat(quotes.get().getFirst().serviceName()).contains("Express");
        assertThat(quotes.get().getFirst().totalCostAud()).isEqualTo(30.4);
    }

    @Test
    void quotes_whenResponseMissingArray_returnsEmptyList() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithResponse(Map.of());
        given(settingsService.getShippitApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isPresent();
        assertThat(quotes.get()).isEmpty();
    }

    @Test
    void quotes_whenWebClientResponseException_returnsEmpty() {
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
        given(settingsService.getShippitApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isEmpty();
    }

    @Test
    void quotes_whenWebClientException_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithError(new WebClientException("timeout") {});
        given(settingsService.getShippitApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isEmpty();
    }
}
