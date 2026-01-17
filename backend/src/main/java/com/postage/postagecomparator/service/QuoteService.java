package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;

public interface QuoteService {

    QuoteResult calculateQuote(ShipmentRequest request);
}
