package com.example.aistore.repository;

import com.example.aistore.entity.Order;
import com.example.aistore.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByOrderNumber(String orderNumber);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.finalAmount), 0) from Order o where o.status not in ('CANCELLED', 'RETURNED')")
    java.math.BigDecimal sumRevenue();

    @Query("select coalesce(sum(o.finalAmount), 0) from Order o where o.status = 'DELIVERED'")
    java.math.BigDecimal sumDeliveredRevenue();

    @Query("select coalesce(avg(o.finalAmount), 0) from Order o where o.status not in ('CANCELLED', 'RETURNED')")
    java.math.BigDecimal avgOrderValue();

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countByStatusGroup();

    @Query("select o from Order o where o.user.id = :userId order by o.createdAt desc")
    List<Order> findByUserId(@Param("userId") Long userId);

    @Query("select count(distinct o.user.id) from Order o where o.status not in ('CANCELLED', 'RETURNED')")
    long countBuyingUsers();
}