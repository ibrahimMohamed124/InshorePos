package com.inshore.customer.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inshore.customer.domain.Customer;
import com.inshore.shared.web.ApiResponse;
import com.inshore.customer.service.CustomerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<Customer> createCustomer(
            @RequestBody Customer customer
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerService.createCustomer(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable Long id,
            @RequestBody Customer customer
    ) throws Exception {
        return ResponseEntity.ok(customerService.updateCustomer(id, customer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomer(
            @PathVariable Long id
    ) throws Exception {
        return ResponseEntity.ok(customerService.getCustomer(id));
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() throws Exception {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Customer>> searchCustomers(
            @RequestParam(required = false) String query
    ) throws Exception {
        return ResponseEntity.ok(customerService.searchCustomers(query));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCustomer(
            @PathVariable Long id
    ) throws Exception {
        customerService.deleteCustomer(id);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setSuccess(true);
        apiResponse.setMessage("Customer deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }
}
