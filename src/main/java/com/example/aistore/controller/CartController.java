package com.example.aistore.controller;

import com.example.aistore.dto.ApiResponse;
import com.example.aistore.dto.CartActionRequest;
import com.example.aistore.dto.CartDto;
import com.example.aistore.entity.Cart;
import com.example.aistore.service.CartService;
import com.example.aistore.service.ProductService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final ProductService productService;

    public CartController(CartService cartService, ProductService productService) {
        this.cartService = cartService;
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<CartDto> view(@AuthenticationPrincipal(expression = "username") String email,
                                     @RequestParam(required = false) String sessionId) {
        return ApiResponse.ok(cartService.viewCartFor(resolveUser(email), sessionId));
    }

    @GetMapping("/count")
    public ApiResponse<Map<String, Integer>> count(@AuthenticationPrincipal(expression = "username") String email,
                                                   @RequestParam(required = false) String sessionId) {
        CartDto dto = cartService.viewCartFor(resolveUser(email), sessionId);
        return ApiResponse.ok(Map.of("count", dto.itemCount()));
    }

    @PostMapping("/add")
    public ApiResponse<CartDto> add(@AuthenticationPrincipal(expression = "username") String email,
                                    @RequestBody CartActionRequest request) {
        CartDto dto = cartService.addItem(resolveUser(email), request);
        recordInteraction(email, request.productId(), "ADD_TO_CART");
        return ApiResponse.ok(dto);
    }

    @PostMapping("/update")
    public ApiResponse<CartDto> update(@AuthenticationPrincipal(expression = "username") String email,
                                       @RequestParam Long cartId,
                                       @RequestParam Long productId,
                                       @RequestParam int quantity) {
        return ApiResponse.ok(cartService.updateQuantity(cartId, productId, quantity));
    }

    @PostMapping("/remove")
    public ApiResponse<CartDto> remove(@AuthenticationPrincipal(expression = "username") String email,
                                       @RequestParam Long cartId,
                                       @RequestParam Long productId) {
        return ApiResponse.ok(cartService.removeItem(cartId, productId));
    }

    private void recordInteraction(String email, Long productId, String type) {
        try {
            if (email == null) {
                return;
            }
            var product = productService.getEntityById(productId);
            cartService.recordCartInteraction(email, type, productId,
                    product.getCategory() != null ? product.getCategory().getName() : null,
                    product.getBrand() != null ? product.getBrand().getName() : null);
        } catch (Exception ignored) {
        }
    }

    private com.example.aistore.entity.User resolveUser(String email) {
        return email == null ? null : productService.findUserByEmail(email);
    }
}