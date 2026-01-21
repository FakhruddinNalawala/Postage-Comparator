package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.WeightBracket;

import java.util.List;

public interface SettingsService {

    OriginSettings getOriginSettings();

    OriginSettings updateOriginSettings(OriginSettings newSettings);

    OriginSettings updateThemePreference(String themePreference);

    String getAusPostApiKey();
    // Sendle integration is currently disabled.
    // String getSendleApiKey();
    // String getSendleId();

    /**
     * Returns the list of weight brackets used for AusPost rule-based pricing.
     * These brackets define weight ranges (in kg) and corresponding prices for standard and express services.
     *
     * @return unmodifiable list of WeightBracket objects
     */
    List<WeightBracket> getAusPostWeightBrackets();

    String getShippitApiKey();

    String getShipStationApiKey();

    String getAfterShipApiKey();
}
