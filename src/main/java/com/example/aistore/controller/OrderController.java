package com.example.aistore.controller;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.CheckoutRequest;
import com.example.aistore.dto.OrderResponse;
import com.example.aistore.entity.OrderStatus;
import com.example.aistore.service.OrderService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final com.example.aistore.service.ProductService productService;

    public OrderController(OrderService orderService,
                           com.example.aistore.service.ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }

    @PostMapping("/checkout")
    public ApiResponse<OrderResponse> checkout(@AuthenticationPrincipal(expression = "username") String email,
                                               @RequestBody CheckoutRequest request) {
        return ApiResponse.ok("Order placed successfully", orderService.checkout(requireUser(email), request));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderResponse>> myOrders(@AuthenticationPrincipal(expression = "username") String email) {
        return ApiResponse.ok(orderService.userOrders(requireUser(email).getId()));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> orderDetails(@AuthenticationPrincipal(expression = "username") String email,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrder(requireUser(email).getId(), id));
    }

    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal(expression = "username") String email,
                                             @PathVariable Long id,
                                             @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(orderService.cancelOrder(requireUser(email).getId(), id, reason));
    }

    private com.example.aistore.entity.User requireUser(String email) {
        if (email == null) {
            throw new com.example.aistore.exception.BadRequestException("Please log in to continue");
        }
        return productService.findUserByEmail(email);
    }
}