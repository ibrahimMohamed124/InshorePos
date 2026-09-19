package com.inshore.shift.domain;

import com.inshore.branch.domain.Branch;
import com.inshore.order.domain.Order;
import com.inshore.product.domain.Product;
import com.inshore.refund.domain.Refund;
import com.inshore.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "shift_reports", indexes = {
        @Index(name = "idx_shift_report_cashier_id", columnList = "cashier_id"),
        @Index(name = "idx_shift_report_branch_id", columnList = "branch_id"),
        // Powers "does this cashier already have an open shift" (shiftEnd IS
        // NULL) and "find the shift covering this date/time" lookups without
        // a full table scan.
        @Index(name = "idx_shift_report_cashier_shift_start", columnList = "cashier_id, shift_start"),
        @Index(name = "idx_shift_report_branch_shift_start", columnList = "branch_id, shift_start")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;

    private Double totalSale;
    private Double totalRefunds;
    private Double netSale;

    // Was Double - a count of orders is a whole number, and treating it as a
    // Double invites the mapper/report to accidentally render "12.0 orders".
    private Long totalOrders;

    @ManyToOne
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    // Computed fresh from Order/Refund aggregates whenever a report is built
    // (see ShiftReportServiceImpl) - never persisted, so a later change to a
    // payment type's totals never goes stale on an old, already-closed report
    // row.
    @Transient
    private List<PaymentSummary> paymentSummary;

    // These were previously @OneToMany(cascade = CascadeType.ALL) with no
    // mappedBy. With no mappedBy/owning FK, Hibernate silently backs a
    // "@OneToMany" like this with a join table - which is really a
    // many-to-many relationship (many shift reports can share a top-selling
    // product; many shift reports can share a recent order) - and
    // cascade = ALL meant deleting a ShiftReport cascaded a REMOVE onto the
    // real, shared Product/Order rows themselves. A shift report is a
    // read-mostly snapshot; it must never be able to delete the actual
    // products or orders it merely references. Switched to @ManyToMany with
    // an explicit, indexed join table and no cascade at all - the products/
    // orders placed here always already exist (they come straight out of
    // OrderRepository queries in ShiftReportServiceImpl), so nothing here
    // ever needs to persist or delete them, only link/unlink the join rows.
    @ManyToMany
    @JoinTable(
            name = "shift_report_top_selling_products",
            joinColumns = @JoinColumn(name = "shift_report_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"),
            indexes = {
                    @Index(name = "idx_srtsp_shift_report_id", columnList = "shift_report_id"),
                    @Index(name = "idx_srtsp_product_id", columnList = "product_id")
            }
    )
    @Builder.Default
    private List<Product> topSellingProducts = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "shift_report_recent_orders",
            joinColumns = @JoinColumn(name = "shift_report_id"),
            inverseJoinColumns = @JoinColumn(name = "order_id"),
            indexes = {
                    @Index(name = "idx_srro_shift_report_id", columnList = "shift_report_id"),
                    @Index(name = "idx_srro_order_id", columnList = "order_id")
            }
    )
    @Builder.Default
    private List<Order> recentOrders = new ArrayList<>();

    // The owning side is Refund.shiftReport (see Refund.java) - this is only
    // the inverse/read side, so it never needs its own cascade to delete real
    // refund rows. A refund is a financial record in its own right; closing
    // or deleting a report must never take a refund down with it.
    @OneToMany(mappedBy = "shiftReport")
    private List<Refund> refunds;

}
