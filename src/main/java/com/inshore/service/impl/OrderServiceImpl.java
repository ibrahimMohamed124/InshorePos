package com.inshore.service.impl;

import com.inshore.domain.OrderStatus;
import com.inshore.domain.PaymentType;
import com.inshore.domain.UserRole;
import com.inshore.exceptions.CustomerNotFoundException;
import com.inshore.exceptions.InsufficientStockException;
import com.inshore.exceptions.OrderNotFoundException;
import com.inshore.mapper.OrderMapper;
import com.inshore.models.*;
import com.inshore.payload.dto.OrderDTO;
import com.inshore.payload.dto.OrderItemDTO;
import com.inshore.repository.BranchRepository;
import com.inshore.repository.CustomerRepository;
import com.inshore.repository.InventoryRepository;
import com.inshore.repository.OrderRepository;
import com.inshore.repository.ProductRepository;
import com.inshore.service.OrderService;
import com.inshore.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public OrderDTO createOrder(OrderDTO orderDTO) throws Exception {
        if (orderDTO == null) {
            throw new IllegalArgumentException("order payload is required");
        }
        if (CollectionUtils.isEmpty(orderDTO.getItems())) {
            throw new IllegalArgumentException("an order must contain at least one item");
        }
        if (orderDTO.getPaymentType() == null) {
            throw new IllegalArgumentException("paymentType is required");
        }

        User cashier = userService.getCurrentUser();

        Branch branch = cashier.getBranch();
        if (branch == null) {
            throw new IllegalStateException(
                    "cashier's branch not found - assign a branch to this user before taking orders");
        }

        Customer customer = null;
        if (orderDTO.getCustomerId() != null) {
            customer = customerRepository.findById(orderDTO.getCustomerId())
                    .orElseThrow(() -> new CustomerNotFoundException("customer not found"));
        }

        Order order = Order.builder()
                .branch(branch)
                .cashier(cashier)
                .customer(customer)
                .paymentType(orderDTO.getPaymentType())
                .status(OrderStatus.PENDING)
                .build();

        for (OrderItemDTO itemDto : orderDTO.getItems()) {
            if (itemDto == null) {
                throw new IllegalArgumentException("order items cannot contain a null entry");
            }
            if (itemDto.getProductId() == null) {
                throw new IllegalArgumentException("productId is required for every order item");
            }
            if (itemDto.getQuantity() == null || itemDto.getQuantity() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than zero");
            }
        }

        // Lock rows in a consistent order (by productId) across every order that
        // goes through this method. Otherwise two multi-item orders that share
        // products but list them in a different order can each hold the lock the
        // other needs and deadlock instead of one simply waiting for the other.
        List<OrderItemDTO> sortedItems = new ArrayList<>(orderDTO.getItems());
        sortedItems.sort((a, b) -> Long.compare(a.getProductId(), b.getProductId()));

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemDTO itemDto : sortedItems) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("product not found: " + itemDto.getProductId()));

            // A product only makes sense to sell out of a branch that belongs to the
            // same store it was created under - otherwise a cashier in Store A could
            // ring up a Store B product that Store A has no inventory record for.
            if (product.getStore() == null || branch.getStore() == null
                    || !product.getStore().getId().equals(branch.getStore().getId())) {
                throw new IllegalArgumentException(
                        "product " + product.getId() + " does not belong to this branch's store");
            }

            if (product.getSellingPrice() == null) {
                throw new IllegalStateException(
                        "product " + product.getId() + " has no selling price configured");
            }

            // Pessimistic write lock: without it, two orders for the same product/
            // branch created at the same instant can both read the same stock count
            // before either writes back, and both pass the check - overselling the
            // last unit twice. The lock is held until this @Transactional method
            // commits or rolls back.
            Inventory inventory = inventoryRepository.findByProductIdAndBranchIdForUpdate(
                    product.getId(), branch.getId());
            if (inventory == null || inventory.getQuantity() == null
                    || inventory.getQuantity() < itemDto.getQuantity()) {
                throw new InsufficientStockException("insufficient stock for product " + product.getName());
            }

            inventory.setQuantity(inventory.getQuantity() - itemDto.getQuantity());
            inventoryRepository.save(inventory);

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemDto.getQuantity())
                    // Price is always computed server-side from the product's current
                    // selling price and captured on the line item, never trusted from
                    // the client and never re-derived later if the product price changes.
                    .price(product.getSellingPrice() * itemDto.getQuantity())
                    .order(order)
                    .build();
            orderItems.add(item);
        }

        double total = orderItems.stream().mapToDouble(OrderItem::getPrice).sum();
        order.setTotalAmount(total);
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);
        return OrderMapper.toDTO(savedOrder);
    }

    @Override
    public OrderDTO getOrderById(Long id) throws Exception {
        Order order = findOrderOrThrow(id);
        checkBranchAccess(userService.getCurrentUser(), order.getBranch());
        return OrderMapper.toDTO(order);
    }

    @Override
    public List<OrderDTO> getOrderByBranch(
            Long branchId,
            Long customerId,
            UUID cashierId,
            PaymentType paymentType,
            OrderStatus orderStatus
    ) throws Exception {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("branch not found"));

        checkBranchAccess(userService.getCurrentUser(), branch);

        return orderRepository.findByBranchId(branchId).stream()
                .filter(o -> customerId == null
                        || (o.getCustomer() != null && customerId.equals(o.getCustomer().getId())))
                .filter(o -> cashierId == null
                        || (o.getCashier() != null && cashierId.equals(o.getCashier().getId())))
                .filter(o -> paymentType == null || paymentType.equals(o.getPaymentType()))
                .filter(o -> orderStatus == null || orderStatus.equals(o.getStatus()))
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderDTO> getOrderByCashier(UUID cashierId) throws Exception {
        if (cashierId == null) {
            throw new IllegalArgumentException("cashierId is required");
        }

        User currentUser = userService.getCurrentUser();
        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new EntityNotFoundException("cashier not found");
        }

        // a cashier can always see their own sales; viewing someone else's requires
        // branch-level authority
        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, cashier.getBranch());
        }

        return orderRepository.findByCashierId(cashierId).stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) throws Exception {
        Order order = findOrderOrThrow(id);
        checkBranchAccess(userService.getCurrentUser(), order.getBranch());

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new IllegalStateException(
                    "a " + order.getStatus() + " order cannot be deleted - it is a financial record");
        }

        // Only restock if the order is still PENDING. A CANCELLED order already
        // had its stock returned by updateOrderStatus - restocking again here
        // would double-credit the inventory for the same order.
        if (order.getStatus() == OrderStatus.PENDING) {
            restockItems(order);
        }
        orderRepository.delete(order);
    }

    @Override
    @Transactional
    public OrderDTO updateOrderStatus(Long id, OrderStatus status) throws Exception {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }

        Order order = findOrderOrThrow(id);
        checkBranchAccess(userService.getCurrentUser(), order.getBranch());

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("cannot change the status of a " + order.getStatus() + " order");
        }

        if (status == OrderStatus.CANCELLED) {
            restockItems(order);
        }

        order.setStatus(status);
        return OrderMapper.toDTO(orderRepository.save(order));
    }

    @Override
    public List<OrderDTO> getTodayOrdersByBranch(Long branchId) throws Exception {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("branch not found"));

        checkBranchAccess(userService.getCurrentUser(), branch);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);

        return orderRepository.findByBranchIdAndCreatedAtBetween(branchId, startOfDay, endOfDay).stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderDTO> getOrdersByCustomerId(Long customerId) throws Exception {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException("customer not found");
        }

        User currentUser = userService.getCurrentUser();

        // Customers aren't scoped to a single store/branch, so their order history
        // can span branches the caller has no business seeing - filter down to
        // branches the current user actually has access to instead of leaking
        // another store's sales data.
        return orderRepository.findByCustomerId(customerId).stream()
                .filter(order -> hasBranchAccess(currentUser, order.getBranch()))
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderDTO> getTop5RecentOrdersByBranchId(Long branchId) throws Exception {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("branch not found"));

        checkBranchAccess(userService.getCurrentUser(), branch);

        return orderRepository.findTop5ByBranchIdOrderByCreatedAtDesc(branchId).stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    private Order findOrderOrThrow(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("order not found"));
    }

    private void restockItems(Order order) {
        if (CollectionUtils.isEmpty(order.getItems())) {
            return;
        }
        // Same fixed lock ordering as createOrder, to avoid deadlocking against a
        // concurrent order/cancellation that touches the same products.
        List<OrderItem> sortedItems = new ArrayList<>(order.getItems());
        sortedItems.sort(Comparator.comparingLong(a -> a.getProduct().getId()));

        for (OrderItem item : sortedItems) {
            Inventory inventory = inventoryRepository.findByProductIdAndBranchIdForUpdate(
                    item.getProduct().getId(), order.getBranch().getId());
            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
                inventoryRepository.save(inventory);
            }
        }
    }

    private void checkBranchAccess(User user, Branch branch) {
        if (!hasBranchAccess(user, branch)) {
            throw new AccessDeniedException("you don't have permission to access orders for this branch");
        }
    }

    private boolean hasBranchAccess(User user, Branch branch) {
        if (branch == null) {
            return true;
        }

        boolean isAdmin = user.getRole() == UserRole.ROLE_ADMIN;

        boolean isStoreAdmin = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && branch.getStore() != null
                && branch.getStore().getStoreAdmin() != null
                && branch.getStore().getStoreAdmin().getId().equals(user.getId());

        boolean isStoreManager = user.getRole() == UserRole.ROLE_STORE_MANAGER
                && branch.getStore() != null
                && user.getStore() != null
                && user.getStore().getId().equals(branch.getStore().getId());

        boolean isBranchManager = user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && branch.getManager() != null
                && branch.getManager().getId().equals(user.getId());

        boolean isOwnBranchStaff = user.getBranch() != null
                && user.getBranch().getId().equals(branch.getId());

        return isAdmin || isStoreAdmin || isStoreManager || isBranchManager || isOwnBranchStaff;
    }
}
