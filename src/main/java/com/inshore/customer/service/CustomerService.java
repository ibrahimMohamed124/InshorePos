package com.inshore.customer.service;

import com.inshore.customer.domain.Customer;

import java.util.List;

public interface CustomerService {

    Customer createCustomer(Customer customer);
    Customer updateCustomer(Long id, Customer customer) throws Exception;
    Customer getCustomer(Long id) throws Exception;
    void deleteCustomer(Long id) throws Exception;
    List<Customer> getAllCustomers() throws Exception;
    List<Customer> searchCustomers(String searchText) throws Exception;


}
