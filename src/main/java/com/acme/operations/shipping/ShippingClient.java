package com.acme.operations.shipping;

import com.acme.operations.common.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;

// Spring crea y gestiona este componente para poder inyectarlo donde se necesita.
@Component
public class ShippingClient {
    // Logger de diagnóstico; informa del fallo sin volcar la respuesta remota completa.
    private static final Logger log = LoggerFactory.getLogger(ShippingClient.class);
    // Cliente HTTP reutilizable con URL base y límites de espera ya configurados.
    private final CarrierApi client;

    // DTO de cotización: referencia y coste recibidos del servicio externo.
    public record Quote(String reference, BigDecimal cost) {}

    // Spring implementa este contrato HTTP y convierte la respuesta en Quote.
    public interface CarrierApi {
        @GetExchange("/quotes")
        Quote quote(@RequestParam("orderId") long orderId,
                    @RequestParam("amount") BigDecimal amount,
                    @RequestHeader(value = "X-Request-ID", required = false) String requestId);
    }

    // Spring pasa la URL y los timeouts de configuración al construir el cliente.
    public ShippingClient(
            // La URL base se define fuera del código para poder cambiar el destino por entorno.
            @Value("${shipping.base-url}") String baseUrl,
            // Límite para abrir conexión; el siguiente parámetro limita la espera de lectura.
            @Value("${shipping.connect-timeout:2s}") Duration connectTimeout,
            @Value("${shipping.read-timeout:3s}") Duration readTimeout) {
        // Crea el transporte HTTP con tiempo máximo para establecer conexión.
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        // Limita la espera de respuesta para evitar que la petición quede pendiente indefinidamente.
        factory.setReadTimeout(readTimeout);
        // Construye el cliente; todavía no llama al servicio remoto.
        var restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(CarrierApi.class);
    }

    // Solicita una cotización de solo lectura; este método no crea un envío real.
    public Quote quote(long orderId, BigDecimal amount) {
        // Agrupa fallos de transporte y de contenido para traducirlos a un error de negocio.
        try {
            // Recupera el identificador de trazabilidad de la petición actual.
            String requestId = MDC.get("requestId");
            // Spring envía los parámetros y convierte automáticamente el JSON en Quote.
            Quote quote = client.quote(orderId, amount, requestId);
            // Rechaza cotizaciones incompletas, referencias inválidas o importes fuera del formato admitido.
            if (quote == null
                    || quote.reference() == null
                    || quote.reference().isBlank()
                    || quote.reference().length() > 100
                    || quote.cost() == null
                    || quote.cost().signum() < 0
                    || quote.cost().scale() > 2
                    || quote.cost().compareTo(new BigDecimal("999999999999.99")) > 0)
                // Un contenido inválido entra en el catch igual que un fallo HTTP.
                throw new IllegalArgumentException("Invalid shipping quote");
            // Devuelve la cotización validada para que el servicio pueda completar el pedido.
            return quote;
        // Traduce fallos HTTP o de validación a un 503; no hace reintentos automáticos.
        } catch (RestClientException | IllegalArgumentException ex) {
            // Registra pedido y tipo de error para diagnóstico.
            log.warn(
                    "Shipping quote failed for order {}: {}",
                    orderId,
                    ex.getClass().getSimpleName());
            // El handler devuelve 503; el llamador no continúa al tramo que consume inventario.
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SHIPPING_UNAVAILABLE",
                    "Shipping service unavailable; order remains confirmed and reserved, retry"
                        + " later");
        }
    }
}
