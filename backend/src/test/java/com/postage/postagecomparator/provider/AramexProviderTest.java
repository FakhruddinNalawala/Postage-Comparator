package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteRequestHelper;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AramexProviderTest {

    @Mock
    private QuoteRequestHelper requestHelper;

    private AramexProvider buildProviderWithResponse(String responseXml) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.contentType(any())).thenReturn(uriSpec);
        when(uriSpec.accept(any())).thenReturn(uriSpec);
        when(uriSpec.header(any(), any())).thenReturn(uriSpec);
        doReturn(headersSpec).when(uriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(responseXml == null ? Mono.empty() : Mono.just(responseXml));

        return new AramexProvider(webClient, requestHelper);
    }

    private AramexProvider buildProviderWithError(Throwable error) {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.contentType(any())).thenReturn(uriSpec);
        when(uriSpec.accept(any())).thenReturn(uriSpec);
        when(uriSpec.header(any(), any())).thenReturn(uriSpec);
        doReturn(headersSpec).when(uriSpec).bodyValue(any());
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(error));

        return new AramexProvider(webClient, requestHelper);
    }

    private AramexProvider buildProviderWithoutStubs() {
        return new AramexProvider(mock(WebClient.class), requestHelper);
    }

    @AfterEach
    void clearEnv() {
        System.clearProperty("ARAMEX_USERNAME");
        System.clearProperty("ARAMEX_PASSWORD");
        System.clearProperty("ARAMEX_ACCOUNT_NUMBER");
        System.clearProperty("ARAMEX_ACCOUNT_PIN");
        System.clearProperty("ARAMEX_ACCOUNT_ENTITY");
        System.clearProperty("ARAMEX_ACCOUNT_COUNTRY");
    }

    @Test
    void quote_whenCredentialsMissing_returnsEmpty() {
        System.setProperty("ARAMEX_USERNAME", "");
        System.setProperty("ARAMEX_PASSWORD", "");
        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        var provider = buildProviderWithoutStubs();

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenResponseHasErrors_returnsEmpty() {
        String responseXml = """
                <RateCalculatorResponse xmlns="http://ws.aramex.net/ShippingAPI/v1/">
                  <HasErrors>true</HasErrors>
                  <Notifications>
                    <Notification>
                      <Message>Invalid request</Message>
                    </Notification>
                  </Notifications>
                </RateCalculatorResponse>
                """;

        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        setAramexProps();
        var provider = buildProviderWithResponse(responseXml);
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenCurrencyNotAud_returnsEmpty() {
        String responseXml = """
                <RateCalculatorResponse xmlns="http://ws.aramex.net/ShippingAPI/v1/">
                  <HasErrors>false</HasErrors>
                  <TotalAmount>
                    <CurrencyCode>USD</CurrencyCode>
                    <Value>12.34</Value>
                  </TotalAmount>
                </RateCalculatorResponse>
                """;

        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        setAramexProps();
        var provider = buildProviderWithResponse(responseXml);
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenValidResponse_returnsQuote() {
        String responseXml = """
                <RateCalculatorResponse xmlns="http://ws.aramex.net/ShippingAPI/v1/">
                  <HasErrors>false</HasErrors>
                  <TotalAmount>
                    <CurrencyCode>AUD</CurrencyCode>
                    <Value>15.25</Value>
                  </TotalAmount>
                </RateCalculatorResponse>
                """;

        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        setAramexProps();
        var provider = buildProviderWithResponse(responseXml);
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isPresent();
        assertThat(quote.get().totalCostAud()).isEqualTo(15.25);
        assertThat(quote.get().pricingSource()).isEqualTo("ARAMEX_API");
    }

    @Test
    void quote_whenWebClientResponseException_returnsEmpty() {
        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        var exception = WebClientResponseException.create(
                500,
                "Internal Server Error",
                null,
                null,
                null
        );
        setAramexProps();
        var provider = buildProviderWithError(exception);
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    @Test
    void quote_whenWebClientException_returnsEmpty() {
        var origin = new OriginSettings("3004", "Melbourne", "VIC", "AU", null, Instant.now());
        var packaging = new Packaging("pack-1", "Box", null, 40, 20, 20, 1000, 2.0);
        var request = new ShipmentRequest("2008", "Darlington", "NSW", "AU", List.of(), "pack-1", false);

        setAramexProps();
        var provider = buildProviderWithError(new WebClientException("timeout") {});
        given(requestHelper.buildDestination(request))
                .willReturn(new QuoteResult.Destination("2008", "Darlington", "NSW", "AU"));
        given(requestHelper.calculateTotalWeight(request.items())).willReturn(600);

        Optional<CarrierQuote> quote = provider.quote(request, origin, packaging, List.of());

        assertThat(quote).isEmpty();
    }

    private void setAramexProps() {
        System.setProperty("ARAMEX_USERNAME", "user");
        System.setProperty("ARAMEX_PASSWORD", "pass");
        System.setProperty("ARAMEX_ACCOUNT_NUMBER", "123");
        System.setProperty("ARAMEX_ACCOUNT_PIN", "456");
        System.setProperty("ARAMEX_ACCOUNT_ENTITY", "AMM");
        System.setProperty("ARAMEX_ACCOUNT_COUNTRY", "JO");
    }
}
