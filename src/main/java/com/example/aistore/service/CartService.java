package com.example.aistore.service;

import com.example.aistore.dto.CartActionRequest;
import com.example.aistore.dto.CartDto;
import com.example.aistore.dto.CartItemDto;
import com.example.aistore.entity.Cart;
import com.example.aistore.entity.CartItem;
import com.example.aistore.entity.Product;
import com.example.aistore.entity.User;
import com.example.aistore.exception.BadRequestException;
import com.example.aistore.exception.ResourceNotFoundException;
import com.example.aistore.repository.CartItemRepository;
import com.example.aistore.repository.CartRepository;
import com.example.aistore.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final TelemetryService telemetryService;
    private final com.example.aistore.repository.UserRepository userRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository, ProductMapper mapper,
                       TelemetryService telemetryService,
                       com.example.aistore.repository.UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.telemetryService = telemetryService;
        this.userRepository = userRepository;
    }

    public void recordCartInteraction(String email, String type, Long productId, String category, String brand) {
        userRepository.findByEmail(email).ifPresent(user ->
                telemetryService.recordInteraction(user.getId(),
                        new com.example.aistore.dto.TelemetryRequest("cart", type, productId, category, brand, null, 0)));
    }

    @Transactional
    public Cart getOrCreateCart(User user, String sessionId) {
        Cart cart = null;
        if (user != null) {
            cart = cartRepository.findByUserId(user.getId()).orElse(null);
        }
        if (cart == null && sessionId != null && !sessionId.isBlank()) {
            cart = cartRepository.findBySessionId(sessionId).orElse(null);
            if (cart != null && user != null) {
                cart.setUser(user);
            }
        }
        if (cart == null) {
            cart = new Cart();
            cart.setUser(user);
            cart.setSessionId(sessionId != null && !sessionId.isBlank() ? sessionId : "session-" + System.nanoTime());
            cart = cartRepository.save(cart);
        }
        return cart;
    }

    @Transactional
    public CartDto addItem(User user, CartActionRequest request) {
        Product product = productRepository.findById(request.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        if (product.getStock() <= 0) {
            throw new BadRequestException("Product is out of stock");
        }
        int qty = request.quantity() == null ? 1 : request.quantity();
        if (qty < 1 || qty > 10) {
            throw new BadRequestException("Quantity must be between 1 and 10");
        }
        if (qty > product.getStock()) {
            throw new BadRequestException("Only " + product.getStock() + " units available in stock");
        }
        Cart cart = getOrCreateCart(user, request.sessionId());
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(qty);
            item.setUnitPrice(product.getPrice());
        } else {
            item.setQuantity(item.getQuantity() + qty);
            if (item.getQuantity() > product.getStock()) {
                throw new BadRequestException("Only " + product.getStock() + " units available in stock");
            }
        }
        cartItemRepository.save(item);
        cart.getItems().add(item);
        return viewCart(cart.getId());
    }

    @Transactional
    public CartDto updateQuantity(Long cartId, Long productId, int quantity) {
        CartItem item = cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item for product", productId));
        if (quantity < 1) {
            throw new BadRequestException("Quantity must be at least 1");
        }
        Product product = item.getProduct();
        if (quantity > product.getStock()) {
            throw new BadRequestException("Only " + product.getStock() + " units available in stock");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return viewCart(cartId);
    }

    @Transactional
    public CartDto removeItem(Long cartId, Long productId) {
        cartItemRepository.deleteByCartAndProduct(cartId, productId);
        return viewCart(cartId);
    }

    @Transactional
    public void clearCart(Long cartId) {
        cartItemRepository.deleteAllByCartId(cartId);
    }

    @Transactional(readOnly = true)
    public CartDto viewCart(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", cartId));
        return toDto(cart);
    }

    @Transactional(readOnly = true)
    public CartDto viewCartFor(User user, String sessionId) {
        if (user != null) {
            return cartRepository.findByUserId(user.getId()).map(this::toDto)
                    .orElseGet(() -> new CartDto(null, List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return cartRepository.findBySessionId(sessionId).map(this::toDto)
                    .orElseGet(() -> new CartDto(null, List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return new CartDto(null, List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public CartDto toDto(Cart cart) {
        List<CartItemDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        int count = 0;
        for (CartItem item : cart.getItems()) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (item.getProduct().getOriginalPrice() != null) {
                BigDecimal lineOriginal = item.getProduct().getOriginalPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                totalDiscount = totalDiscount.add(lineOriginal.subtract(lineTotal));
            }
            subtotal = subtotal.add(lineTotal);
            count += item.getQuantity();
            items.add(new CartItemDto(item.getId(),
                    mapper.toCard(item.getProduct()), item.getQuantity(), item.getUnitPrice(), lineTotal));
        }
        return new CartDto(cart.getId(), items, count, subtotal, totalDiscount);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getItems(Long cartId) {
        return cartItemRepository.findByCartId(cartId);
    }
}