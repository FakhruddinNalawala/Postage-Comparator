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
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipStationProviderTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private QuoteRequestHelper requestHelper;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ShipStationProvider buildProviderWithResponse(List<Map<String, Object>> response) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString()))
                .thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(List.class))
                .thenReturn(response == null ? Mono.empty() : Mono.just(response));

        return new ShipStationProvider(webClient, settingsService, requestHelper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ShipStationProvider buildProviderWithError(Throwable error) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString()))
                .thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(List.class)).thenReturn(Mono.error(error));

        return new ShipStationProvider(webClient, settingsService, requestHelper);
    }

    private ShipStationProvider buildProviderWithoutStubs() {
        return new ShipStationProvider(mock(WebClient.class), settingsService, requestHelper);
    }

    @Test
    void quotes_whenApiKeyMissing_returnsEmpty() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithoutStubs();
        given(settingsService.getShipStationApiKey()).willReturn(" ");

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isEmpty();
    }

    @Test
    void quotes_whenValidResponse_mapsQuotes() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        List<Map<String, Object>> response = List.of(
                Map.of(
                        "service_code", "service_one",
                        "delivery_days", 3,
                        "shipping_amount", Map.of("amount", 12.0)
                ),
                Map.of(
                        "service_code", "service_two",
                        "delivery_days", 2,
                        "shipping_amount", Map.of("amount", 8.0)
                )
        );

        var provider = buildProviderWithResponse(response);
        given(settingsService.getShipStationApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isPresent();
        assertThat(quotes.get()).hasSize(2);
        assertThat(quotes.get().getFirst().pricingSource()).isEqualTo("SHIPSTATION_API");
    }

    @Test
    void quote_whenMultipleRates_returnsCheapest() {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1000, 2.0);
        var request = new ShipmentRequest("3000", "Melbourne", "VIC", "AU", List.of(), "pack-1", false);

        List<Map<String, Object>> response = List.of(
                Map.of(
                        "service_code", "service_one",
                        "delivery_days", 3,
                        "shipping_amount", Map.of("amount", 12.0)
                ),
                Map.of(
                        "service_code", "service_two",
                        "delivery_days", 2,
                        "shipping_amount", Map.of("amount", 8.0)
                )
        );

        var provider = buildProviderWithResponse(response);
        given(settingsService.getShipStationApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isPresent();
        assertThat(quote.get().totalCostAud()).isEqualTo(8.0);
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
        given(settingsService.getShipStationApiKey()).willReturn("key");
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
        given(settingsService.getShipStationApiKey()).willReturn("key");
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(500);

        Optional<List<CarrierQuote>> quotes = provider.quotes(request, origin, packaging, List.of());

        assertThat(quotes).isEmpty();
    }
}
