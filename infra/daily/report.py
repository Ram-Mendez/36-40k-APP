"""Parte de operaciones del laboratorio. Python 3.8+ y cliente PostgreSQL."""

import argparse
from datetime import datetime, timezone
from decimal import Decimal
import json
import os
from pathlib import Path
import subprocess
from urllib.error import HTTPError, URLError
from urllib.request import urlopen


# Un único SELECT proporciona una instantánea consistente, incluso durante una confirmación.
SNAPSHOT = """
WITH refunds AS (
    SELECT r.id, r.order_id, r.updated_at, r.credit_amount::text AS header,
           coalesce(sum(l.quantity*l.unit_price),0)::numeric(16,2)::text AS lines
    FROM public.sales_return r LEFT JOIN public.sales_return_line l ON l.return_id=r.id
    GROUP BY r.id
    HAVING r.credit_amount <> coalesce(sum(l.quantity*l.unit_price),0)
), reservations AS (
    SELECT o.id, o.status, sum(s.quantity) AS units
    FROM public.stock_reservation s JOIN public.sales_order o ON o.id=s.order_id
    WHERE o.status <> 'CONFIRMED'
    GROUP BY o.id
)
SELECT json_build_object(
    'at', now(), 'since', now() - interval '24 hours',
    'orders', (SELECT count(*) FROM public.sales_order
              WHERE created_at BETWEEN now() - interval '24 hours' AND now()),
    'returns', (SELECT count(*) FROM public.sales_return
               WHERE created_at BETWEEN now() - interval '24 hours' AND now()),
    'bad_returns', (SELECT count(*) FROM refunds),
    'refund_samples', (SELECT coalesce(json_agg(row_to_json(s)), '[]')
                       FROM (SELECT * FROM refunds ORDER BY updated_at DESC,id DESC LIMIT 3) s),
    'bad_orders', (SELECT count(*) FROM reservations),
    'order_samples', (SELECT coalesce(json_agg(row_to_json(s)), '[]')
                      FROM (SELECT * FROM reservations ORDER BY id DESC LIMIT 3) s)
);
"""


# Solo eventos de negocio emitidos por los servicios. Se excluye el texto libre de auditoría.
TOP_EVENTS = """
SELECT coalesce(json_agg(row_to_json(e) ORDER BY n DESC, action), '[]')
FROM (
    SELECT action, count(*) AS n
    FROM public.audit_event
    WHERE created_at BETWEEN :'cutoff'::timestamptz - interval '24 hours' AND :'cutoff'::timestamptz
      AND action IN (
          'CUSTOMER_CREATED', 'CUSTOMER_UPDATED', 'CUSTOMER_DEACTIVATED',
          'PRODUCT_CREATED', 'PRODUCT_UPDATED', 'PRODUCT_DEACTIVATED',
          'ORDER_CREATED', 'ORDER_UPDATED', 'ORDER_LINE_SET', 'ORDER_LINE_REMOVED',
          'ORDER_CONFIRMED', 'ORDER_CANCELLED', 'ORDER_COMPLETED', 'STOCK_ADJUSTED',
          'RETURN_REQUESTED', 'RETURN_APPROVED', 'RETURN_REJECTED', 'RETURN_RECEIVED',
          'INVOICE_ISSUED', 'INVOICE_PAYMENT_RECORDED', 'INVOICE_REFUND_RECORDED', 'INVOICE_ENTRY_REVERSED',
          'SUPPLIER_CREATED', 'SUPPLIER_UPDATED', 'SUPPLIER_DEACTIVATED',
          'PURCHASE_ORDER_CREATED', 'PURCHASE_LINE_SET', 'PURCHASE_LINE_REMOVED',
          'PURCHASE_ORDER_SUBMITTED', 'PURCHASE_ORDER_CANCELLED', 'PURCHASE_RECEIVED',
          'REPLENISHMENT_POLICY_CREATED', 'REPLENISHMENT_POLICY_UPDATED'
      )
    GROUP BY action ORDER BY n DESC, action LIMIT 3
) e;
"""


def readiness():
    url = os.environ.get(
        "BACKEND_HEALTH_URL", "http://127.0.0.1:38088/actuator/health/readiness"
    )
    try:
        with urlopen(url, timeout=4) as response:
            body = response.read(32768)
    except HTTPError as exc:
        if exc.code != 503:
            return "Desconocida (respuesta HTTP %d)." % exc.code, False
        body = exc.read(32768)
    except (URLError, OSError, ValueError):
        return "Desconocida. El proceso del parte no pudo consultar el backend.", False
    try:
        payload = json.loads(body)
    except (UnicodeError, ValueError):
        payload = None
    status = payload.get("status") if isinstance(payload, dict) else None
    if status == "UP":
        return "UP.", False
    if status in ("DOWN", "OUT_OF_SERVICE"):
        return status + ".", True
    return "Desconocida. El endpoint no devolvió un estado reconocible.", False


def query(sql, *variables):
    env = dict(os.environ, PGCONNECT_TIMEOUT="4", PGCLIENTENCODING="UTF8",
               PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=8000 -c timezone=UTC")
    try:
        result = subprocess.run(
            [os.environ.get("PSQL", "psql"), "-X", "-w", "-Atq",
             "-v", "ON_ERROR_STOP=1", *variables, "-f", "-"],
            env=env, input=sql.encode("utf-8"), capture_output=True, check=True, timeout=15,
        )
        return json.loads(result.stdout)
    except (OSError, subprocess.SubprocessError, ValueError):
        # El error del cliente podría contener detalles de conexión ajenos al parte.
        return None


def snapshot():
    data = query(SNAPSHOT)
    if data is not None:
        data["events"] = query(TOP_EVENTS, "-v", "cutoff=" + data["at"])
    return data


def euros(value):
    return f"{Decimal(value):.2f}".replace(".", ",") + " €"


def utc_minute(value):
    return datetime.fromisoformat(value).astimezone(timezone.utc).strftime("%d/%m/%Y %H:%M UTC")


def render(at, health, unhealthy, data):
    lines = [f"Parte de backend Java · 36-40k-APP · {at:%d/%m/%Y %H:%M UTC}",
             "", f"Disponibilidad del backend en el momento de la consulta: {health}"]
    actions = []
    if unhealthy:
        actions.append("Reproduce la consulta de readiness en la app y revisa en la consola de Backend APP qué dependencia falla.")
    if data is None:
        lines.append("PostgreSQL: sin lectura. La actividad, los abonos y las reservas quedan sin verificar.")
    else:
        lines.extend([
            "", f"Actividad del laboratorio del {utc_minute(data['since'])} al {utc_minute(data['at'])}:",
            f"Pedidos creados: {data['orders']}. Devoluciones solicitadas: {data['returns']}.",
        ])
        if data["events"] is None:
            lines.append("Eventos de negocio: auditoría sin lectura.")
        elif data["events"]:
            lines.append("Eventos de negocio más frecuentes: " +
                         ", ".join(f"{e['action']} ({e['n']})" for e in data["events"]) + ".")
        else:
            lines.append("Eventos de negocio: ninguno de los tipos conocidos en esta ventana.")
        lines.extend([
            "", "Controles sobre todos los registros guardados al cierre:",
            f"• Devoluciones cuyo abono difiere de la suma de sus líneas: {data['bad_returns']}.",
        ])
        for item in data["refund_samples"]:
            lines.append(f"  Devolución {item['id']}, pedido {item['order_id']}: "
                         f"cabecera {euros(item['header'])}, líneas {euros(item['lines'])}. "
                         f"Última modificación {utc_minute(item['updated_at'])}.")
        if data["refund_samples"]:
            item = data["refund_samples"][0]
            actions.append(f"Abre GET /api/v1/returns/{item['id']} en Swagger y compara creditAmount con las líneas. "
                           "Para saber si el código actual sigue guardando un abono erróneo, reproduce una devolución nueva siguiendo INC-02.")
        lines.append(f"• Pedidos con reservas guardadas en un estado distinto de CONFIRMED: {data['bad_orders']}.")
        for item in data["order_samples"]:
            lines.append(f"  Pedido {item['id']}: estado {item['status']}, {item['units']} unidades reservadas.")
        if data["order_samples"]:
            item = data["order_samples"][0]
            actions.append(f"Consulta GET /api/v1/orders/{item['id']} y sus movimientos de inventario. "
                           "Sigue la transacción del cambio de estado en el depurador y verifica sus reservas con un pedido nuevo.")
    if actions:
        lines.extend(["", "Tu trabajo como desarrollador backend"])
        lines.extend(f"{i}. {action}" for i, action in enumerate(actions, 1))
    elif data is not None:
        lines.extend(["", "Los controles de abonos y reservas no han encontrado incidencias."])
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("daily-report.txt"))
    args = parser.parse_args()
    at = datetime.now(timezone.utc)
    health, unhealthy = readiness()
    data = snapshot()
    args.output.write_text(render(at, health, unhealthy, data), encoding="utf-8")
    # Un parte parcial sigue siendo entregable. Jenkins puede registrar esta línea.
    print("Parte guardado:", args.output, "| PostgreSQL:", "leído" if data is not None else "sin lectura",
          "| Backend:", health)


if __name__ == "__main__":
    main()
