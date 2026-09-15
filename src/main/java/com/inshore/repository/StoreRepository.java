package com.inshore.repository;

import com.inshore.models.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, Long> {

    // "storeAdmin" is the field name on Store, so Spring Data derives
    // the query path storeAdmin.id from this method name.
    Store findByStoreAdminId(UUID id);

}
