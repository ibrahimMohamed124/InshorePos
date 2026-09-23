package com.inshore.seeder;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.inshore.branch.domain.Branch;
import com.inshore.branch.repository.BranchRepository;
import com.inshore.category.domain.Category;
import com.inshore.category.repository.CategoryRepository;
import com.inshore.customer.domain.Customer;
import com.inshore.customer.repository.CustomerRepository;
import com.inshore.inventory.domain.Inventory;
import com.inshore.inventory.repository.InventoryRepository;
import com.inshore.order.domain.Order;
import com.inshore.order.domain.OrderItem;
import com.inshore.order.domain.OrderStatus;
import com.inshore.order.domain.PaymentType;
import com.inshore.order.repository.OrderRepository;
import com.inshore.product.domain.Product;
import com.inshore.product.repository.ProductRepository;
import com.inshore.refund.domain.Refund;
import com.inshore.refund.repository.RefundRepository;
import com.inshore.store.domain.Store;
import com.inshore.store.domain.StoreContact;
import com.inshore.store.domain.StoreStatus;
import com.inshore.store.repository.StoreRepository;
import com.inshore.user.domain.User;
import com.inshore.user.domain.UserRole;
import com.inshore.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;



import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the database with a small, self-consistent set of sample data so the
 * app is immediately usable when run locally.
 *
 * Only wired up for the "dev" profile (activate with
 * -Dspring-boot.run.profiles=dev or spring.profiles.active=dev), and skips
 * seeding entirely if a Store already exists, so it's safe to restart the
 * app repeatedly without creating duplicates.
 *
 * All seeded users share the password: Passw0rd!123
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "Passw0rd!123";

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (storeRepository.count() > 0) {
            log.info("DataSeeder: data already present, skipping seed.");
            return;
        }

        log.info("DataSeeder: seeding dev data...");

        Store store = seedStore();
        List<Branch> branches = seedBranches(store);
        Branch downtown = branches.get(0);
        Branch uptown = branches.get(1);

        List<User> cashiers = seedCashiers(store, downtown, uptown);
        User downtownCashier = cashiers.get(0);
        User uptownCashier = cashiers.get(1);

        List<Category> categories = seedCategories(store);
        List<Product> products = seedProducts(store, categories);
        seedInventory(products, branches);

        List<Customer> customers = seedCustomers(store);

        seedOrders(downtown, uptown, downtownCashier, uptownCashier, products, customers);

        log.info("DataSeeder: done. Seeded 1 store, {} branches, {} products, {} customers.",
                branches.size(), products.size(), customers.size());
    }

    private User newUser(String username, String email, String phone, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setPhone(phone);
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setLastLoginAt(LocalDateTime.now());
        return user;
    }

    private Store seedStore() {
        // storeAdmin <-> Store is a two-way link, so the admin user has to be
        // saved once without a store, then re-saved once the store (which
        // needs the admin's id) exists.
        User storeAdmin = newUser("storeadmin", "storeadmin@inshore.test", "+201000000001",
                UserRole.ROLE_STORE_ADMIN);
        storeAdmin = userRepository.save(storeAdmin);

        Store store = new Store();
        store.setBrand("Inshore Coffee Co.");
        store.setDescription("Specialty coffee and light bites, seeded for local development.");
        store.setStoreType("Cafe");
        store.setStoreAdmin(storeAdmin);
        store.setContact(StoreContact.builder()
                .address("12 Corniche St, Alexandria, Egypt")
                .phone("+203000000001")
                .email("contact@inshore.test")
                .build());
        store = storeRepository.save(store); // @PrePersist sets status to PENDING
        store.setStatus(StoreStatus.ACTIVE);
        store = storeRepository.save(store);

        storeAdmin.setStore(store);
        userRepository.save(storeAdmin);

        return store;
    }

    private List<Branch> seedBranches(Store store) {
        User downtownManager = userRepository.save(
                newUser("downtown.manager", "downtown.manager@inshore.test", "+201000000002",
                        UserRole.ROLE_BRANCH_MANAGER));
        downtownManager.setStore(store);

        User uptownManager = userRepository.save(
                newUser("uptown.manager", "uptown.manager@inshore.test", "+201000000003",
                        UserRole.ROLE_BRANCH_MANAGER));
        uptownManager.setStore(store);         

        Branch downtown = Branch.builder()
                .name("Downtown Branch")
                .address("5 Saad Zaghloul St, Alexandria, Egypt")
                .phone("+203000000002")
                .email("downtown@inshore.test")
                .workingDays(List.of("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"))
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(23, 0))
                .store(store)
                .manager(downtownManager)
                .build();
        downtown = branchRepository.save(downtown);

        Branch uptown = Branch.builder()
                .name("Uptown Branch")
                .address("40 Fouad St, Alexandria, Egypt")
                .phone("+203000000003")
                .email("uptown@inshore.test")
                .workingDays(List.of("SUN", "MON", "TUE", "WED", "THU"))
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(22, 0))
                .store(store)
                .manager(uptownManager)
                .build();
        uptown = branchRepository.save(uptown);

        downtownManager.setBranch(downtown);
        userRepository.save(downtownManager);
        uptownManager.setBranch(uptown);
        userRepository.save(uptownManager);

        return List.of(downtown, uptown);
    }

    private List<User> seedCashiers(Store store, Branch downtown, Branch uptown) {
        User downtownCashier = newUser("downtown.cashier", "downtown.cashier@inshore.test",
                "+201000000004", UserRole.ROLE_CASHIER);
        downtownCashier.setStore(store);
        downtownCashier.setBranch(downtown);
        downtownCashier = userRepository.save(downtownCashier);

        User uptownCashier = newUser("uptown.cashier", "uptown.cashier@inshore.test",
                "+201000000005", UserRole.ROLE_CASHIER);
        uptownCashier.setStore(store);
        uptownCashier.setBranch(uptown);
        uptownCashier = userRepository.save(uptownCashier);

        return List.of(downtownCashier, uptownCashier);
    }

    private List<Category> seedCategories(Store store) {
        Category beverages = categoryRepository.save(
                Category.builder().name("Beverages").store(store).build());
        Category bakery = categoryRepository.save(
                Category.builder().name("Bakery").store(store).build());
        Category snacks = categoryRepository.save(
                Category.builder().name("Snacks").store(store).build());
        return List.of(beverages, bakery, snacks);
    }

    private List<Product> seedProducts(Store store, List<Category> categories) {
        Category beverages = categories.get(0);
        Category bakery = categories.get(1);
        Category snacks = categories.get(2);

        Product espresso = Product.builder()
                .name("Espresso").sku("ESP-001")
                .description("Double shot of espresso.")
                .mrp(60.0).sellingPrice(55.0).brand("Inshore")
                .category(beverages).store(store)
                .build();

        Product cappuccino = Product.builder()
                .name("Cappuccino").sku("CAP-001")
                .description("Espresso with steamed milk foam.")
                .mrp(85.0).sellingPrice(80.0).brand("Inshore")
                .category(beverages).store(store)
                .build();

        Product croissant = Product.builder()
                .name("Butter Croissant").sku("CRO-001")
                .description("Freshly baked butter croissant.")
                .mrp(45.0).sellingPrice(40.0).brand("Inshore Bakery")
                .category(bakery).store(store)
                .build();

        Product muffin = Product.builder()
                .name("Blueberry Muffin").sku("MUF-001")
                .description("Muffin with real blueberries.")
                .mrp(55.0).sellingPrice(50.0).brand("Inshore Bakery")
                .category(bakery).store(store)
                .build();

        Product chips = Product.builder()
                .name("Potato Chips").sku("CHP-001")
                .description("Salted potato chips, 40g bag.")
                .mrp(25.0).sellingPrice(25.0).brand("CrispCo")
                .category(snacks).store(store)
                .build();

        return productRepository.saveAll(List.of(espresso, cappuccino, croissant, muffin, chips));
    }

    private void seedInventory(List<Product> products, List<Branch> branches) {
        for (Branch branch : branches) {
            for (Product product : products) {
                Inventory inventory = Inventory.builder()
                        .branch(branch)
                        .product(product)
                        .quantity(50)
                        .build();
                inventoryRepository.save(inventory);
            }
        }
    }

    private List<Customer> seedCustomers(Store store) {
        Customer sara = Customer.builder()
                .fullName("Sara Ahmed").email("sara.ahmed@example.com").phone("+201111111111")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).store(store)
                .build();
        Customer omar = Customer.builder()
                .fullName("Omar Khaled").email("omar.khaled@example.com").phone("+201222222222")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).store(store)
                .build();
        Customer layla = Customer.builder()
                .fullName("Layla Hassan").email("layla.hassan@example.com").phone("+201333333333")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).store(store)
                .build();

        return customerRepository.saveAll(List.of(sara, omar, layla));
    }

    private void seedOrders(Branch downtown, Branch uptown, User downtownCashier, User uptownCashier,
                            List<Product> products, List<Customer> customers) {
        Product espresso = products.get(0);
        Product cappuccino = products.get(1);
        Product croissant = products.get(2);
        Product muffin = products.get(3);

        // Order 1: completed, downtown, paid by card - refunded afterward.
        Order completedOrder = Order.builder()
                .branch(downtown)
                .cashier(downtownCashier)
                .customer(customers.get(0))
                .paymentType(PaymentType.CARD)
                .status(OrderStatus.COMPLETED)
                .build();
        addItem(completedOrder, cappuccino, 2);
        addItem(completedOrder, croissant, 1);
        completedOrder.setTotalAmount(totalOf(completedOrder));
        completedOrder = orderRepository.save(completedOrder);

        Refund refund = Refund.builder()
                .order(completedOrder)
                .reason("Customer changed their mind about the croissant.")
                .amount(croissant.getSellingPrice())
                .cashier(downtownCashier)
                .branch(downtown)
                .paymentType(PaymentType.CARD)
                .build();
        refundRepository.save(refund);

        // Order 2: pending, uptown, cash.
        Order pendingOrder = Order.builder()
                .branch(uptown)
                .cashier(uptownCashier)
                .customer(customers.get(1))
                .paymentType(PaymentType.CASH)
                .status(OrderStatus.PENDING)
                .build();
        addItem(pendingOrder, espresso, 1);
        addItem(pendingOrder, muffin, 1);
        pendingOrder.setTotalAmount(totalOf(pendingOrder));
        orderRepository.save(pendingOrder);

        // Order 3: completed, downtown, mobile money, no customer on file.
        Order walkInOrder = Order.builder()
                .branch(downtown)
                .cashier(downtownCashier)
                .paymentType(PaymentType.MOBILE_MONEY)
                .status(OrderStatus.COMPLETED)
                .build();
        addItem(walkInOrder, espresso, 3);
        walkInOrder.setTotalAmount(totalOf(walkInOrder));
        orderRepository.save(walkInOrder);
    }

    private void addItem(Order order, Product product, int quantity) {
        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(quantity)
                // OrderItem.price is the LINE total (unit price x quantity), exactly as
                // OrderServiceImpl stores it for real sales - so reports and refunds add up.
                .price(product.getSellingPrice() * quantity)
                .build();
        order.getItems().add(item);
    }

    private double totalOf(Order order) {
        return order.getItems().stream()
                .mapToDouble(OrderItem::getPrice)
                .sum();
    }
}