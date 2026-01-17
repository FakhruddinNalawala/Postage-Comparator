package com.postage.postagecomparator.api;

import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentRequest;
import com.postage.postagecomparator.service.QuoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<QuoteResult> createQuote(@RequestBody @Valid ShipmentRequest request) {
        QuoteResult result = quoteService.calculateQuote(request);
        return ResponseEntity.ok(result);
    }

}
