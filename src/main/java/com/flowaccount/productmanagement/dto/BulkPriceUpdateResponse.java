package com.flowaccount.productmanagement.dto;

import java.util.List;

public record BulkPriceUpdateResponse(
        int totalRequested,
        int updatedCount,
        int failedCount,
        List<BulkPriceUpdateFailure> failures) {
}
