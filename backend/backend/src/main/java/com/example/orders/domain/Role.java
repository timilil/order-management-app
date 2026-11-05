package com.example.orders.domain;

/** The three kinds of users. Stored as text in the {@code users.role} column. */
public enum Role {
    CUSTOMER,
    SUPPORT,
    ADMIN;

    /** SUPPORT and ADMIN work with orders of all customers. */
    public boolean isStaff() {
        return this == SUPPORT || this == ADMIN;
    }
}
