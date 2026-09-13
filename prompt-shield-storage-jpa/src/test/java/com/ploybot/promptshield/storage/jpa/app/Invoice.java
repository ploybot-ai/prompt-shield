package com.ploybot.promptshield.storage.jpa.app;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** An entity of the host application, to check its scanning is not replaced. */
@Entity
public class Invoice {

    @Id
    private Long id;

    private String customer;

    protected Invoice() {
    }

    public Invoice(Long id, String customer) {
        this.id = id;
        this.customer = customer;
    }

    public String getCustomer() {
        return customer;
    }
}
