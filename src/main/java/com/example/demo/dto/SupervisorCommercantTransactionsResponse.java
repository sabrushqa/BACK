package com.example.demo.dto;

import java.util.List;

public record SupervisorCommercantTransactionsResponse(
    Long commercantId,
    String commercantNom,
    List<MerchantSessionResponse.TransactionItem> transactions
) {
}
