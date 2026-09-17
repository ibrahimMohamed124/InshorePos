package com.inshore.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inshore.domain.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Order order;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Double amount;

    // Optional: ShiftReport has no repository/service yet (it's an empty stub -
    // see ShiftReport.java), so there's currently no way to resolve "the
    // cashier's active shift" when a refund is created. Left nullable so
    // refunds can start being attributed to a shift once that lands, without
    // another migration.
    @ManyToOne
    @JsonIgnore
    private ShiftReport shiftReport;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User cashier;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Branch branch;

    // Was missing @Enumerated here: without it JPA stores the enum by ORDINAL,
    // so reordering or inserting a PaymentType constant later would silently
    // reinterpret every already-stored refund as a different payment type.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
