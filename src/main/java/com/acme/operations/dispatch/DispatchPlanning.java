package com.acme.operations.dispatch;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;
import com.acme.operations.order.OrderService;
import com.acme.operations.product.ProductDtos;
import com.acme.operations.product.ProductService;
import com.acme.operations.shipping.ShippingClient;
import java.time.Instant;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("turno-cero")
class DispatchPlanning {
    record Label(String barcode, String sku, String wave) {}

    private final JdbcTemplate jdbc;
    private final DispatchConfiguration.DispatchProperties settings;
    private final OrderService orders;
    private final ProductService products;
    private final ShippingClient shipping;

    DispatchPlanning(JdbcTemplate jdbc, DispatchConfiguration.DispatchProperties settings,
            OrderService orders, ProductService products, ShippingClient shipping) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.orders = orders;
        this.products = products;
        this.shipping = shipping;
    }

    Instant closeAt(Instant openedAt) {
        return openedAt.plusSeconds(settings.loadingWindow().toMinutes());
    }

    Label label(String barcode) {
        return jdbc.query("""
                SELECT l.barcode,p.sku,l.wave FROM dispatch_label l
                JOIN product p ON p.id=l.product_id WHERE l.barcode=?
                """, (rs, row) -> new Label(rs.getString("barcode"), rs.getString("sku"),
                        rs.getString("wave")), barcode)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "LABEL_NOT_FOUND", "No parcel matches this barcode"));
    }

    @Cacheable(cacheNames = "dock-capacity", key = "#dock")
    public int capacity(String wave, String dock) {
        return jdbc.query("SELECT capacity FROM dispatch_dock WHERE wave=? AND dock=?",
                        (rs, row) -> rs.getInt("capacity"), wave, dock)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "DOCK_NOT_ASSIGNED", "This dock is not assigned to the wave"));
    }

    ShippingClient.Quote quote(long orderId) {
        var order = orders.get(orderId);
        return shipping.quote(order.id(), order.total().movePointRight(2));
    }

    PageResponse<ProductDtos.View> catalog(int page) {
        return products.list(page, 2, "sku", "asc", null, true);
    }
}
