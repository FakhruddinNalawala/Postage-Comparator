package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.*;
import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.provider.CarrierProvider;
import com.postage.postagecomparator.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class QuoteServiceImplTest {

    @Mock
    private SettingsService settingsService;

    @Mock
    private ItemService itemService;

    @Mock
    private PackagingService packagingService;

    @Mock
    private ProviderRegistry providerRegistry;

    @Mock
    private ProviderConfig providerConfig;

    @Mock
    private CarrierProvider ausPostProvider;

    // We don't exercise HTTP clients in these unit tests; APIs are disabled via missing keys.
    private QuoteServiceImpl quoteService;

    @BeforeEach
    void setUp() {
        var requestHelper = new QuoteRequestHelper(settingsService, itemService, packagingService);
        quoteService = new QuoteServiceImpl(
                settingsService,
                requestHelper,
                providerRegistry,
                providerConfig
        );
        lenient().when(providerRegistry.getEnabledProviders(providerConfig))
                .thenReturn(List.of(ausPostProvider));
        lenient().when(ausPostProvider.getName()).thenReturn("auspost");
        lenient().when(ausPostProvider.quote(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.empty());
        lenient().when(ausPostProvider.quotes(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.empty());
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
                null
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
                null
        );

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one item is required");

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
                null
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
                null
        );

        given(settingsService.getOriginSettings()).willReturn(null);

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Origin settings must be configured");
    }

    @Test
    void calculateQuote_whenNoPackagingAvailable_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                null
        );

        var item = new Item("item-1", "Widget", null, 100, 5, 5, 5);
        given(settingsService.getOriginSettings())
                .willReturn(new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now()));
        given(itemService.findById("item-1")).willReturn(Optional.of(item));
        given(packagingService.findAll()).willReturn(List.of());

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No packaging available");
    }

    @Test
    void calculateQuote_whenNoPackagingFitsItemVolume_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                null
        );

        // Item has volume 10*10*10 = 1000 cm³
        var item = new Item("item-1", "Large Widget", null, 100, 10, 10, 10);
        // Packaging has volume 5*5*5 = 125 cm³ (too small)
        var smallPackaging = new Packaging("pack-1", "Small Box", null, 5, 5, 5, 1.0);
        
        given(settingsService.getOriginSettings())
                .willReturn(new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now()));
        given(itemService.findById("item-1")).willReturn(Optional.of(item));
        given(packagingService.findAll()).willReturn(List.of(smallPackaging));

        assertThatThrownBy(() -> quoteService.calculateQuote(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No packaging found that can fit");
    }

    @Test
    void calculateQuote_whenItemNotFound_throwsIllegalArgumentException() {
        var request = new ShipmentRequest(
                "3000",
                "Melbourne",
                "VIC",
                "AU",
                List.of(new ShipmentItemSelection("item-1", 1)),
                null
        );

        given(settingsService.getOriginSettings())
                .willReturn(new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now()));
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
                null
        );

        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        // Item volume: 5*5*5 = 125 cm³, 2 items = 250 cm³
        var item = new Item("item-1", "Widget", null, 250, 5, 5, 5);
        // Packaging volume: 10*10*10 = 1000 cm³ (fits 250 cm³)
        var packaging = new Packaging("pack-1", "Small box", null, 10, 10, 10, 2.0);

        // 2 items * 250g = 500g
        given(settingsService.getOriginSettings()).willReturn(origin);
        given(itemService.findById("item-1")).willReturn(Optional.of(item));
        given(packagingService.findAll()).willReturn(List.of(packaging));

        // Provide brackets: weight bracket matches 0.5kg (actual) but NOT 0.25kg (volume);
        // volume bracket matches 0.25kg (volume-weight) but NOT 0.5kg. So both match and
        // delivery cost uses the higher (volume-based) price.
        var weightBracket = new WeightBracket(0.4, 1.0, 10.0, 15.0);
        var volumeBracket = new WeightBracket(0.2, 0.3, 20.0, 25.0);
        given(settingsService.getAusPostWeightBrackets())
                .willReturn(List.of(weightBracket, volumeBracket));

        QuoteResult result = quoteService.calculateQuote(request);

        assertThat(result.totalWeightGrams()).isEqualTo(500);
        assertThat(result.weightInKg()).isEqualTo(0.5);
        assertThat(result.origin()).isEqualTo(origin);
        assertThat(result.destination().postcode()).isEqualTo("3000");
        assertThat(result.destination().country()).isEqualTo("AU"); // defaulted
        assertThat(result.packaging()).isEqualTo(packaging);

        // We now expect both standard and express AusPost rules-based quotes.
        assertThat(result.carrierQuotes()).hasSize(2);

        var standardQuote = result.carrierQuotes().stream()
                .filter(q -> !q.isExpress())
                .findFirst()
                .orElseThrow();
        var expressQuote = result.carrierQuotes().stream()
                .filter(CarrierQuote::isExpress)
                .findFirst()
                .orElseThrow();

        // Standard (rules-based Parcel Post)
        assertThat(standardQuote.carrier()).isEqualTo("AUSPOST");
        assertThat(standardQuote.serviceName()).contains("Parcel Post");
        assertThat(standardQuote.pricingSource()).isEqualTo("RULES");
        assertThat(standardQuote.ruleFallbackUsed()).isTrue();
        assertThat(standardQuote.deliveryCostAud()).isEqualTo(20.0);
        assertThat(standardQuote.packagingCostAud()).isEqualTo(2.0);
        assertThat(standardQuote.totalCostAud()).isEqualTo(22.0);

        // Express (rules-based Express Post)
        assertThat(expressQuote.carrier()).isEqualTo("AUSPOST");
        assertThat(expressQuote.serviceName()).contains("Express Post");
        assertThat(expressQuote.pricingSource()).isEqualTo("RULES");
        assertThat(expressQuote.ruleFallbackUsed()).isTrue();
        assertThat(expressQuote.deliveryCostAud()).isEqualTo(25.0);
        assertThat(expressQuote.packagingCostAud()).isEqualTo(2.0);
        assertThat(expressQuote.totalCostAud()).isEqualTo(27.0);

        // Sendle integration disabled, so only AusPost is returned (standard + express).
    }

    // --- Direct tests of AusPost rules-based pricing for edge cases ---

    @Test
    void calculateAusPostRulesBasedQuote_whenOnlyWeightMatches_usesWeightBracket() throws Exception {
        var origin = new OriginSettings("2000", "Sydney", "NSW", "AU", null, Instant.now());
        var destination = new QuoteResult.Destination("3000", "Melbourne", "VIC", "AU");
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1.0);

        // 500g -> 0.5kg, volumeWeightInKg will be based on dimensions
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
        var packaging = new Packaging("pack-1", "Box", null, 200, 200, 200, 1.0);

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
        var packaging = new Packaging("pack-1", "Box", null, 10, 10, 10, 1.0);

        int totalWeightGrams = 1000; // 1kg
        given(settingsService.getAusPostWeightBrackets())
                .willReturn(List.of(new WeightBracket(2.0, 3.0, 30.0, 35.0)));

        assertThatThrownBy(() -> invokeAusPostRulesBased(origin, destination, totalWeightGrams, packaging, false))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("No bracket found");
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

}

