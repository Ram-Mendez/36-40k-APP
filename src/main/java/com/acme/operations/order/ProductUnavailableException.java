package com.acme.operations.order;

import com.acme.operations.common.BusinessException;
import org.springframework.http.HttpStatus;

final class ProductUnavailableException extends BusinessException {
    ProductUnavailableException() {
        super(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product is missing or inactive");
    }
}
