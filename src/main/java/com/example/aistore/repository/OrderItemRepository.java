package com.example.aistore.repository;

import com.example.aistore.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    @Query("select oi.product.name, sum(oi.quantity) as qty from OrderItem oi group by oi.product.name order by qty desc")
    List<Object[]> topSellingProducts();
}