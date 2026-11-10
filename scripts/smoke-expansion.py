#!/usr/bin/env python3
"""Exercise the expansion through the running API and its real shipping service.

Set LAB_USERNAME and LAB_PASSWORD, then run:
    python3 scripts/smoke-expansion.py --base-url http://127.0.0.1:18086
Each run creates uniquely named lab resources and retains their IDs as evidence;
it does not delete data, configure the server, or expose credentials in its JSON result.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import json
import os
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4


class SmokeFailure(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    # Never forward credentials or bearer headers to an unexpected redirect target.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Smoke:
    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.token: str | None = None
        self.secrets = [password]
        self.opener = build_opener(NoRedirect())
        self.run_id = uuid4().hex
        self.prefix = "SMOKE-" + self.run_id[:16]
        self.resources: dict[str, int] = {}
        self.checks: list[str] = []
        self.step = "initialize"
        self.requests = 0

    def redact(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {
                key: "[REDACTED]" if any(word in key.lower() for word in
                    ("password", "token", "authorization", "secret")) else self.redact(item)
                for key, item in value.items()
            }
        if isinstance(value, list):
            return [self.redact(item) for item in value]
        if isinstance(value, str):
            for secret in self.secrets:
                if secret:
                    value = value.replace(secret, "[REDACTED]")
            return value
        return value

    def call(self, step: str, method: str, path: str, body: Any = None,
             expected: int = 200) -> Any:
        self.step = step
        headers = {"Accept": "application/json", "X-Request-ID": self.prefix + "-" + str(self.requests + 1)}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        data = None
        if body is not None:
            data = json.dumps(body, separators=(",", ":")).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = Request(self.base_url + path, data=data, headers=headers, method=method)
        self.requests += 1
        try:
            with self.opener.open(request, timeout=20) as response:
                status = response.status
                raw = response.read().decode("utf-8", errors="replace")
        except HTTPError as error:
            status = error.code
            raw = error.read().decode("utf-8", errors="replace")
        except (URLError, TimeoutError, OSError) as error:
            raise SmokeFailure(f"{method} {path}: connection failed: {self.redact(str(error))}") from None
        try:
            result = json.loads(raw, parse_float=Decimal) if raw else None
        except json.JSONDecodeError:
            result = raw
        if status != expected:
            safe_body = json.dumps(self.redact(result), ensure_ascii=False, default=str)[:2000]
            raise SmokeFailure(f"{method} {path}: expected HTTP {expected}, received {status}; body={safe_body}")
        return result

    def check(self, label: str, condition: bool) -> None:
        if not condition:
            raise SmokeFailure("Assertion failed: " + label)
        self.checks.append(label)

    def resource(self, name: str, result: dict[str, Any]) -> int:
        resource_id = result.get("id")
        if not isinstance(resource_id, int) or resource_id <= 0:
            raise SmokeFailure("Response has no positive resource id for " + name)
        self.resources[name] = resource_id
        return resource_id

    def run(self) -> dict[str, Any]:
        login = self.call("login", "POST", "/api/v1/auth/login",
                          {"username": self.username, "password": self.password})
        if not isinstance(login, dict) or not isinstance(login.get("accessToken"), str):
            raise SmokeFailure("Login did not return an access token")
        self.token = login["accessToken"]
        self.secrets.extend(value for key, value in login.items()
                            if "token" in key.lower() and isinstance(value, str))
        suffix = self.run_id[:20]
        supplier = self.resource("supplier", self.call("create_supplier", "POST", "/api/v1/suppliers",
            {"name": self.prefix + " supplier", "taxId": "S" + suffix,
             "email": "supplier@example.test", "leadTimeDays": 2}, 201))
        product = self.resource("product", self.call("create_product", "POST", "/api/v1/products",
            {"sku": self.prefix, "name": self.prefix + " product", "unitPrice": 10}, 201))
        purchase = self.resource("purchaseOrder", self.call("create_purchase", "POST", "/api/v1/purchase-orders",
            {"supplierId": supplier}, 201))
        po_path = f"/api/v1/purchase-orders/{purchase}"
        purchase_view = self.call("set_purchase_line", "PUT", f"{po_path}/lines/{product}",
                                 {"quantity": 10, "unitCost": 6})
        self.check("purchase_total_60", Decimal(str(purchase_view["total"])) == Decimal("60"))
        self.call("submit_purchase", "POST", po_path + "/submit")
        receipt_payload = {"requestId": str(uuid4()), "deliveryReference": self.prefix,
                           "lines": [{"productId": product, "quantity": 10}]}
        receipt = self.call("receive_purchase", "POST", po_path + "/receipts", receipt_payload)
        self.resource("purchaseReceipt", receipt)
        replay = self.call("replay_purchase_receipt", "POST", po_path + "/receipts", receipt_payload)
        self.check("purchase_receipt_replay_is_identical", replay == receipt)
        conflict = self.call("reject_changed_receipt_payload", "POST", po_path + "/receipts",
            {**receipt_payload, "lines": [{"productId": product, "quantity": 9}]}, 409)
        self.check("changed_receipt_payload_is_409", conflict["code"] == "IDEMPOTENCY_CONFLICT")
        receipts = self.call("verify_single_purchase_receipt", "GET", po_path + "/receipts")
        self.check("receipt_count_is_one", receipts["totalElements"] == 1
                   and len(receipts["content"]) == 1 and receipts["content"][0]["id"] == receipt["id"])
        bought = self.call("verify_purchase_received", "GET", po_path)
        self.check("purchase_received_exactly_ten", bought["status"] == "RECEIVED"
                   and bought["lines"][0]["receivedQuantity"] == 10)

        customer = self.resource("customer", self.call("create_customer", "POST", "/api/v1/customers",
            {"name": self.prefix + " customer", "taxId": "C" + suffix,
             "email": "customer@example.test", "address": "Laboratory address, Madrid"}, 201))
        sale = self.resource("salesOrder", self.call("create_sale", "POST", "/api/v1/orders",
                                                    {"customerId": customer}, 201))
        sale_path = f"/api/v1/orders/{sale}"
        self.call("set_sales_line", "PUT", f"{sale_path}/lines/{product}", {"quantity": 2})
        self.call("confirm_sale", "POST", sale_path + "/confirm")
        completed = self.call("complete_sale_through_shipping", "POST", sale_path + "/complete")
        self.check("sale_completed", completed["status"] == "COMPLETED")
        self.check("shipping_quote_contract", completed["shippingReference"] == f"QUOTE-{sale}-20.00")
        shipping = Decimal(str(completed["shippingCost"]))
        self.check("shipping_cost_is_nonnegative_cents", shipping >= 0 and shipping == shipping.quantize(Decimal("0.01")))
        due_date = (datetime.now(timezone.utc).date() + timedelta(days=30)).isoformat()
        issued = self.call("issue_invoice", "POST", "/api/v1/billing/invoices",
                           {"orderId": sale, "dueDate": due_date}, 201)
        invoice = self.resource("invoice", issued)
        invoice_path = f"/api/v1/billing/invoices/{invoice}"
        total = Decimal(str(issued["total"]))
        self.check("invoice_includes_products_and_shipping", total == Decimal("20") + shipping
                   and Decimal(str(issued["productTotal"])) == Decimal("20")
                   and Decimal(str(issued["shippingTotal"])) == shipping)
        # JSON numbers are used for the API contract; verify the exact decimal round trip first.
        payment_number = float(total)
        self.check("payment_amount_round_trip", Decimal(str(payment_number)) == total)
        payment_payload = {"requestId": str(uuid4()), "amount": payment_number, "reference": self.prefix + " payment"}
        payment = self.call("record_payment", "POST", invoice_path + "/payments", payment_payload)
        self.resource("payment", payment)
        self.check("payment_replay_is_identical", payment == self.call("replay_payment", "POST",
                                                                      invoice_path + "/payments", payment_payload))

        returned = self.call("request_return", "POST", "/api/v1/returns",
            {"orderId": sale, "requestId": str(uuid4()), "reason": "Lab return of one unit",
             "lines": [{"productId": product, "quantity": 1}]}, 201)
        return_id = self.resource("salesReturn", returned)
        return_path = f"/api/v1/returns/{return_id}"
        approved = self.call("approve_return", "POST", return_path + "/approve",
                             {"version": returned["version"], "reason": "Lab inspection approved"})
        return_payload = {"requestId": str(uuid4()), "version": approved["version"],
                          "lines": [{"productId": product, "restockQuantity": 1, "damagedQuantity": 0}]}
        received_return = self.call("receive_return", "POST", return_path + "/receive", return_payload)
        self.check("return_received_with_credit_10", received_return["status"] == "RECEIVED"
                   and Decimal(str(received_return["creditAmount"])) == Decimal("10"))
        self.check("return_receipt_replay_is_identical", received_return == self.call("replay_return_receipt", "POST",
                                                                                   return_path + "/receive", return_payload))
        before_refund = self.call("verify_credit_due", "GET", invoice_path)
        self.check("returned_unit_creates_credit_due_10", before_refund["status"] == "CREDIT_DUE"
                   and Decimal(str(before_refund["balance"])) == Decimal("-10"))
        refund_payload = {"requestId": str(uuid4()), "amount": 10, "reference": self.prefix + " refund"}
        refund = self.call("record_refund", "POST", invoice_path + "/refunds", refund_payload)
        self.resource("refund", refund)
        self.check("refund_replay_is_identical", refund == self.call("replay_refund", "POST",
                                                                    invoice_path + "/refunds", refund_payload))

        policy = self.call("create_replenishment_policy", "PUT", f"/api/v1/replenishment/policies/{product}",
                          {"minimumStock": 10, "targetStock": 20, "preferredSupplierId": supplier, "active": True})
        self.check("policy_is_for_own_product", policy["productId"] == product and policy["preferredSupplierId"] == supplier)
        proposals = self.call("read_replenishment_proposal", "GET", f"/api/v1/replenishment/proposals?productId={product}")
        self.check("one_proposal_for_own_product", proposals["totalElements"] == 1 and len(proposals["content"]) == 1)
        proposal = proposals["content"][0]
        self.check("proposal_accounts_for_received_purchase_sale_and_return",
                   proposal["productId"] == product and proposal["available"] == 9
                   and proposal["incoming"] == 0 and proposal["projected"] == 9
                   and proposal["suggestedQuantity"] == 11 and proposal["purchasable"] is True)
        balances = self.call("verify_inventory_balance", "GET", f"/api/v1/inventory?productId={product}")
        self.check("single_inventory_balance", balances["totalElements"] == 1 and len(balances["content"]) == 1)
        balance = balances["content"][0]
        self.check("final_stock_9_and_reserved_0", balance["productId"] == product
                   and balance["onHand"] == 9 and balance["reserved"] == 0 and balance["available"] == 9)
        final_invoice = self.call("verify_final_invoice", "GET", invoice_path)
        self.check("final_invoice_is_settled", final_invoice["status"] == "SETTLED"
                   and Decimal(str(final_invoice["balance"])) == 0
                   and Decimal(str(final_invoice["credited"])) == 10
                   and Decimal(str(final_invoice["netPaid"])) == total - Decimal("10"))
        entries = self.call("verify_ledger_has_no_duplicate_settlements", "GET", invoice_path + "/entries")
        self.check("exactly_one_payment_and_one_refund", entries["totalElements"] == 2
                   and {entry["id"] for entry in entries["content"]} == {payment["id"], refund["id"]})
        credits = self.call("verify_credit_origin", "GET", invoice_path + "/credits")
        self.check("single_credit_from_own_return", credits["totalElements"] == 1
                   and credits["content"][0]["returnId"] == return_id
                   and Decimal(str(credits["content"][0]["amount"])) == 10)
        return {
            "status": "PASS", "runId": self.run_id, "resources": self.resources,
            "httpRequests": self.requests, "checks": self.checks,
            "result": {"shippingReference": completed["shippingReference"],
                       "shippingCost": format(shipping, ".2f"), "invoiceTotal": format(total, ".2f"),
                       "credited": "10.00", "netPaid": format(total - Decimal("10"), ".2f"),
                       "invoiceBalance": "0.00", "stock": 9, "reserved": 0,
                       "incoming": 0, "suggestedQuantity": 11, "purchaseReceiptCount": 1},
        }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:18086")
    args = parser.parse_args()
    parsed = urlsplit(args.base_url)
    if (parsed.scheme not in {"http", "https"} or not parsed.hostname
            or parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment):
        print(json.dumps({"status": "FAIL", "step": "configuration", "error": "Use an HTTP(S) base URL without credentials, query or fragment"}))
        return 2
    username, password = os.getenv("LAB_USERNAME"), os.getenv("LAB_PASSWORD")
    if not username or not password:
        print(json.dumps({"status": "FAIL", "step": "configuration", "error": "LAB_USERNAME and LAB_PASSWORD are required"}))
        return 2
    smoke = Smoke(args.base_url, username, password)
    try:
        result = smoke.run()
    except Exception as error:
        result = {"status": "FAIL", "runId": smoke.run_id, "step": smoke.step,
                  "error": smoke.redact(str(error)), "resources": smoke.resources,
                  "httpRequests": smoke.requests, "passedChecks": smoke.checks}
        print(json.dumps(smoke.redact(result), ensure_ascii=False, indent=2, default=str))
        return 1
    print(json.dumps(smoke.redact(result), ensure_ascii=False, indent=2, default=str))
    return 0


if __name__ == "__main__":
    sys.exit(main())
