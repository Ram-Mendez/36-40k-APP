package com.acme.operations.warehouse;

import java.util.List;
import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Profile("dev & !turno-cero")
class TransferRouting {
    record Rule(Predicate<TransferPracticeDtos.ReceiptCondition> matches, String destination) {}

    @Bean
    @Order(10)
    Rule inspectionRoute() {
        return new Rule(TransferPracticeDtos.ReceiptCondition::damaged, "INSPECTION");
    }

    @Bean
    @Order(20)
    Rule coldStorageRoute() {
        return new Rule(TransferPracticeDtos.ReceiptCondition::refrigerated, "COLD_STORAGE");
    }

    @Bean
    @Order(30)
    Rule standardRoute() {
        return new Rule(condition -> true, "STANDARD_STORAGE");
    }

    @Bean
    Router transferReceiptRouter(List<Rule> rules) {
        return new Router(rules);
    }

    record Router(List<Rule> rules) {
        TransferPracticeDtos.Route route(TransferPracticeDtos.ReceiptCondition condition) {
            return rules.stream()
                    .filter(rule -> rule.matches().test(condition))
                    .findFirst()
                    .map(rule -> new TransferPracticeDtos.Route(rule.destination()))
                    .orElseThrow();
        }
    }
}
