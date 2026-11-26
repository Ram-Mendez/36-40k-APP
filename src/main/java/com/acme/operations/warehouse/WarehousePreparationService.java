package com.acme.operations.warehouse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev & !turno-cero")
class WarehousePreparationService {
    private static final int MAX_PARCELS = 20;
    private static final int MAX_KILOGRAMS = 100;

    int packedUnits(List<WarehousePreparationController.PackLine> lines) {
        return lines.stream()
                .mapToInt(line -> line.boxes() * line.unitsPerBox() + line.looseUnits())
                .sum();
    }

    boolean fits(int parcels, int kilograms) {
        return parcels <= MAX_PARCELS && kilograms <= MAX_KILOGRAMS;
    }

    LocalDate arrival(LocalDate departureDate, int workingDays) {
        LocalDate arrivalDate = departureDate;
        int remainingDays = workingDays;

        while (remainingDays > 0) {
            arrivalDate = arrivalDate.plusDays(1);

            DayOfWeek day = arrivalDate.getDayOfWeek();

            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                remainingDays--;
            }
        }

        return arrivalDate;
    }

    List<Integer> cartLoads(int parcels, int carts) {
        int perCart = parcels / carts;
        int remainder = parcels % carts;

        List<Integer> loads = new ArrayList<>();

        for (int i = 0; i < carts; i++) {
            int load = perCart;

            if (i < remainder) {
                load++;
            }

            loads.add(load);
        }

        return loads;
    }

}
