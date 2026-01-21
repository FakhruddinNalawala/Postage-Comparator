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
        lenient().when(ausPostProvider.quote(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(ausPostProvider.quotes(any(), any(), any(), any()))
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

