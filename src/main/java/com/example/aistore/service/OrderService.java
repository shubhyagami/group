package com.example.aistore.service;

import com.example.aistore.config.AppProperties;
import com.example.aistore.dto.CheckoutRequest;
import com.example.aistore.dto.OrderResponse;
import com.example.aistore.entity.Address;
import com.example.aistore.entity.Cart;
import com.example.aistore.entity.CartItem;
import com.example.aistore.entity.Inventory;
import com.example.aistore.entity.Order;
import com.example.aistore.entity.OrderItem;
import com.example.aistore.entity.OrderStatus;
import com.example.aistore.entity.Payment;
import com.example.aistore.entity.PaymentStatus;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.User;
import com.example.aistore.exception.BadRequestException;
import com.example.aistore.exception.ResourceNotFoundException;
import com.example.aistore.repository.AddressRepository;
import com.example.aistore.repository.InventoryRepository;
import com.example.aistore.repository.OrderItemRepository;
import com.example.aistore.repository.OrderRepository;
import com.example.aistore.repository.PaymentRepository;
import com.example.aistore.repository.ProductRepository;
import com.example.aistore.service.email.BrevoEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart, order lifecycle, stock reservation &amp; payment processing service.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CartService cartService;
    private final BrevoEmailService emailService;
    private final AppProperties properties;
    private final TelemetryService telemetryService;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        PaymentRepository paymentRepository, AddressRepository addressRepository,
                        ProductRepository productRepository, InventoryRepository inventoryRepository,
                        CartService cartService, BrevoEmailService emailService,
                        AppProperties properties, TelemetryService telemetryService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.cartService = cartService;
        this.emailService = emailService;
        this.properties = properties;
        this.telemetryService = telemetryService;
    }

    @Transactional
    public OrderResponse checkout(User user, CheckoutRequest request) {
        if (user == null) {
            throw new BadRequestException("Please log in to place an order");
        }
        Address address = addressRepository.findById(request.addressId())
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Address", request.addressId()));

        Cart cart = cartService.getOrCreateCart(user, request.sessionId());
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new BadRequestException("Product " + product.getName() + " is no longer available");
            }
            if (product.getStock() < item.getQuantity()) {
                throw new BadRequestException("Insufficient stock for " + product.getName());
            }
            reserveStock(product, item.getQuantity());

            OrderItem oi = new OrderItem();
            oi.setProduct(product);
            oi.setProductName(product.getName());
            oi.setProductImageUrl(product.getPrimaryImageUrl());
            oi.setQuantity(item.getQuantity());
            oi.setUnitPrice(item.getUnitPrice());
            oi.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItems.add(oi);
            subtotal = subtotal.add(oi.getTotalPrice());
        }

        BigDecimal discount = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (product.getOriginalPrice() != null) {
                BigDecimal lineOriginal = product.getOriginalPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                BigDecimal lineCurrent = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                discount = discount.add(lineOriginal.subtract(lineCurrent).max(BigDecimal.ZERO));
            }
        }

        BigDecimal shippingFee = subtotal.compareTo(properties.shipping().freeAbove()) >= 0
                ? BigDecimal.ZERO : properties.shipping().fee();
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAmount = subtotal.add(tax).add(shippingFee);

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUser(user);
        order.setTotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setTaxAmount(tax);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setShippingAddress(address);
        order.setEstimatedDeliveryDate(LocalDateTime.now().plusDays(5));
        orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
            orderItemRepository.save(oi);
            order.getItems().add(oi);
        }

        boolean isCod = "COD".equalsIgnoreCase(request.paymentMethod());
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(request.paymentMethod() == null ? "CREDIT_CARD" : request.paymentMethod().toUpperCase());
        payment.setAmount(finalAmount);
        payment.setStatus(isCod ? PaymentStatus.PENDING : PaymentStatus.COMPLETED);
        payment.setTransactionId(isCod ? null : "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        payment.setPaidAt(isCod ? null : LocalDateTime.now());
        paymentRepository.save(payment);
        order.setPayment(payment);

        cartService.clearCart(cart.getId());
        orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            telemetryService.recordInteraction(user.getId(),
                    new com.example.aistore.dto.TelemetryRequest("checkout", "PRODUCT_PURCHASE",
                            oi.getProduct().getId(), oi.getProduct().getCategory() != null
                                    ? oi.getProduct().getCategory().getName() : null,
                            oi.getProduct().getBrand() != null ? oi.getProduct().getBrand().getName() : null,
                            null, 0));
        }

        emailService.sendOrderConfirmationEmail(user.getEmail(), user.getFullName(),
                order.getOrderNumber(), finalAmount, buildItemsSummary(orderItems));

        return toResponse(order);
    }

    private void reserveStock(Product product, int quantity) {
        product.setStock(product.getStock() - quantity);
        product.setInStock(product.getStock() > 0);
        productRepository.save(product);
        inventoryRepository.findByProductId(product.getId()).ifPresent(inv -> {
            inv.setReservedQuantity(inv.getReservedQuantity() + quantity);
            inventoryRepository.save(inv);
        });
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId, String reason) {
        Order order = findUserOrder(userId, orderId);
        if (!order.getStatus().name().equals("PENDING") && !order.getStatus().name().equals("CONFIRMED")
                && !order.getStatus().name().equals("PROCESSING")) {
            throw new BadRequestException("Order cannot be cancelled in its current state");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        for (OrderItem oi : order.getItems()) {
            Product product = oi.getProduct();
            product.setStock(product.getStock() + oi.getQuantity());
            product.setInStock(true);
            productRepository.save(product);
        }
        if (order.getPayment() != null && order.getPayment().getStatus() == PaymentStatus.COMPLETED) {
            order.getPayment().setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(order.getPayment());
        }
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.setStatus(status);
        if (status == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            if (order.getPayment() != null) {
                order.getPayment().setStatus(PaymentStatus.COMPLETED);
                order.getPayment().setPaidAt(LocalDateTime.now());
            }
        }
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Order findUserOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Order does not belong to this user");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> userOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        return toResponse(findUserOrder(userId, orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse toResponse(Order order) {
        Address a = order.getShippingAddress();
        OrderResponse.OrderItemResponseDto addressDto = a == null ? null
                : new OrderResponse.OrderItemResponseDto(a.getId(), a.getFullName(), a.getStreetAddress(),
                a.getApartment(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry(), a.getPhone());
        List<OrderResponse.OrderLineDto> lines = order.getItems().stream()
                .map(oi -> new OrderResponse.OrderLineDto(oi.getProduct() != null ? oi.getProduct().getId() : null,
                        oi.getProductName(), oi.getProductImageUrl(), oi.getQuantity(), oi.getUnitPrice(), oi.getTotalPrice()))
                .toList();
        Payment p = order.getPayment();
        return new OrderResponse(order.getId(), order.getOrderNumber(), addressDto,
                order.getTotalAmount(), order.getDiscountAmount(), order.getTaxAmount(), order.getShippingFee(),
                order.getFinalAmount(), order.getStatus().name(),
                order.getCarrier(), order.getTrackingNumber(),
                p != null ? p.getPaymentMethod() : null,
                p != null ? p.getStatus().name() : null,
                p != null ? p.getTransactionId() : null,
                order.getEstimatedDeliveryDate(), order.getDeliveredAt(), order.getCreatedAt(), lines);
    }

    private String generateOrderNumber() {
        return "OM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String buildItemsSummary(List<OrderItem> items) {
        StringBuilder sb = new StringBuilder();
        for (OrderItem oi : items) {
            sb.append("<p style=\"margin:4px 0;\">&#8226; ")
                    .append(escape(oi.getProductName()))
                    .append(" x ").append(oi.getQuantity())
                    .append(" &mdash; &#8377; ").append(oi.getTotalPrice().toPlainString())
                    .append("</p>");
        }
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}