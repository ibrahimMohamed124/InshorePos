package com.inshore.service.impl;

import com.inshore.exceptions.CustomerNotFoundException;
import com.inshore.models.Customer;
import com.inshore.repository.CustomerRepository;
import com.inshore.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private final CustomerRepository customerRepository;

    @Override
    public Customer createCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("customer payload is required");
        }

        if (!StringUtils.hasText(customer.getFullName())) {
            throw new IllegalArgumentException("fullName is required");
        }

        // never trust an id coming from the client on create, otherwise
        // save() would silently turn this into an update of an existing row
        customer.setId(null);

        customer.setFullName(customer.getFullName().trim());
        customer.setEmail(normalizeEmail(customer.getEmail()));
        customer.setPhone(normalizePhone(customer.getPhone()));

        if (customer.getEmail() != null) {
            validateEmail(customer.getEmail());
            ensureEmailIsFree(customer.getEmail(), null);
        }

        customer.setCreatedAt(LocalDateTime.now());
        customer.setUpdatedAt(null);

        return customerRepository.save(customer);
    }

    @Override
    public Customer updateCustomer(Long id, Customer customer) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (customer == null) {
            throw new IllegalArgumentException("customer payload is required");
        }

        Customer existingCustomer = customerRepository.findById(id).orElseThrow(
                () -> new CustomerNotFoundException("Customer not found")
        );

        // Partial update: only touch fields the caller actually sent, and
        // apply them to the persisted entity - not to the incoming payload.
        if (customer.getFullName() != null) {
            if (!StringUtils.hasText(customer.getFullName())) {
                throw new IllegalArgumentException("fullName cannot be blank");
            }
            existingCustomer.setFullName(customer.getFullName().trim());
        }

        if (customer.getEmail() != null) {
            String email = normalizeEmail(customer.getEmail());
            if (email != null) {
                validateEmail(email);
                ensureEmailIsFree(email, existingCustomer.getId());
            }
            existingCustomer.setEmail(email);
        }

        if (customer.getPhone() != null) {
            existingCustomer.setPhone(normalizePhone(customer.getPhone()));
        }

        existingCustomer.setUpdatedAt(LocalDateTime.now());

        return customerRepository.save(existingCustomer);
    }

    @Override
    public Customer getCustomer(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }

        return customerRepository.findById(id).orElseThrow(
                () -> new CustomerNotFoundException("Customer not found")
        );
    }

    @Override
    public void deleteCustomer(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }

        Customer customerToDelete = customerRepository.findById(id).orElseThrow(
                () -> new CustomerNotFoundException("Customer not found")
        );
        customerRepository.delete(customerToDelete);
    }

    @Override
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Override
    public List<Customer> searchCustomers(String searchText) {
        if (!StringUtils.hasText(searchText)) {
            return getAllCustomers();
        }

        return customerRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                searchText.trim(), searchText.trim()
        );
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return phone.trim();
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email is not valid");
        }
    }

    private void ensureEmailIsFree(String email, Long currentCustomerId) {
        customerRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (!existing.getId().equals(currentCustomerId)) {
                throw new IllegalStateException("a customer with this email already exists");
            }
        });
    }
}
