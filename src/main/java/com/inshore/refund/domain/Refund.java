package com.inshore.refund.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.inshore.branch.domain.Branch;
import com.inshore.order.domain.Order;
import com.inshore.order.domain.PaymentType;
import com.inshore.shift.domain.ShiftReport;
import com.inshore.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(indexes = {
        @Index(name = "idx_refund_order_id", columnList = "order_id"),
        @Index(name = "idx_refund_cashier_id", columnList = "cashier_id"),
        @Index(name = "idx_refund_cashier_created_at", columnList = "cashier_id, created_at"),
        @Index(name = "idx_refund_branch_id", columnList = "branch_id"),
        @Index(name = "idx_refund_shift_report_id", columnList = "shift_report_id")
})
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

    // Optional: set by RefundServiceImpl to the cashier's open shift at the
    // time the refund is created, or left null if they had none (e.g. an
    // admin processing a refund outside shift hours).
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
