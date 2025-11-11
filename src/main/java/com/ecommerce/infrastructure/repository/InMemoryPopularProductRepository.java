package com.ecommerce.infrastructure.repository;

import com.ecommerce.domain.repository.PopularProductRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class InMemoryPopularProductRepository implements PopularProductRepository {

    private static class SaleRecord {
        Long productId;
        Integer quantity;
        LocalDateTime orderTime;

        SaleRecord(Long productId, Integer quantity, LocalDateTime orderTime) {
            this.productId = productId;
            this.quantity = quantity;
            this.orderTime = orderTime;
        }
    }

    private final List<SaleRecord> saleRecords = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void recordSale(Long productId, Integer quantity, LocalDateTime orderTime) {
        saleRecords.add(new SaleRecord(productId, quantity, orderTime));
    }

    @Override
    public List<Long> getTopProductIds(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        Map<Long, Integer> productSales = new HashMap<>();

        synchronized (saleRecords) {
            for (SaleRecord record : saleRecords) {
                if (!record.orderTime.isBefore(startTime) && !record.orderTime.isAfter(endTime)) {
                    productSales.merge(record.productId, record.quantity, Integer::sum);
                }
            }
        }

        return productSales.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
