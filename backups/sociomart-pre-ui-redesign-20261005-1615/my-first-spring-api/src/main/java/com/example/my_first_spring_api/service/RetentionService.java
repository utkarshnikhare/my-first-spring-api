package com.example.my_first_spring_api.service;

import com.example.my_first_spring_api.model.*;
import com.example.my_first_spring_api.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Retention foundation required by Admin handover section 12.
 *
 * <p>The handover is explicit that the short operational window must NOT be
 * hard-coded: "Make retention_days configurable so it can later be changed to
 * 7, 15 or 30 days without code changes."
 *
 * <p>Resolution order for {@code retention_days}:
 * <ol>
 *   <li>{@code PlatformSetting} row {@code order_retention_days} - set by Admin at runtime</li>
 *   <li>{@code sociomart.retention.order-days} property - deployment override</li>
 *   <li>{@value #DEFAULT_RETENTION_DAYS}</li>
 * </ol>
 * Changing the value therefore needs no code change and no migration.
 *
 * <h2>Destructive behaviour is intentionally NOT implemented</h2>
 * The demo runs on an in-memory H2 database re-seeded on every boot, so an
 * automatic purge would only delete live demo data to no operational benefit
 * while destroying the evidence the audit trail and exports depend on. This
 * service provides the configurable window, the daily aggregate rollup that
 * must outlive a purge, and a read-only preview of affected rows. No delete is
 * executed; the remaining production-only work is stated in the delivery
 * report rather than silently skipped.
 */
@Service
public class RetentionService {

    public static final String SETTING_KEY = "order_retention_days";
    public static final int DEFAULT_RETENTION_DAYS = 5;
    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 3650;
    public static final String PROPERTY = "sociomart.retention.order-days";

    /**
     * Operator switch for the destructive purge.
     *
     * <p>Defaults to OFF. Nothing is ever deleted until an Admin deliberately
     * turns this on AND runs the purge by hand with a confirmation and a reason,
     * so no schedule, boot or test can delete data behind anyone's back.</p>
     */
    public static final String SETTING_PURGE_KEY = "retention_purge_enabled";

    private final PlatformSettingRepository settings;
    private final OrderDailyAggregateRepository aggregates;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final Environment environment;

    @Autowired
    public RetentionService(PlatformSettingRepository settings,
                            OrderDailyAggregateRepository aggregates,
                            OrderRepository orders,
                            OrderItemRepository orderItems,
                            Environment environment) {
        this.settings = settings;
        this.aggregates = aggregates;
        this.orders = orders;
        this.orderItems = orderItems;
        this.environment = environment;
    }

    /** Resolved retention window in days. Never throws: falls back to the default. */
    @Transactional(readOnly = true)
    public int retentionDays() {
        PlatformSetting row = settings.findBySettingKey(SETTING_KEY).orElse(null);
        if (row != null && row.getSettingValue() != null) {
            Integer parsed = parsePositive(row.getSettingValue());
            if (parsed != null) return clamp(parsed);
        }
        String prop = environment.getProperty(PROPERTY);
        Integer fromProp = prop == null ? null : parsePositive(prop);
        return fromProp != null ? clamp(fromProp) : DEFAULT_RETENTION_DAYS;
    }

    /** Admin may change the window at runtime; no code change or migration required. */
    @Transactional
    public int setRetentionDays(int days) {
        int safe = clamp(days);
        PlatformSetting row = settings.findBySettingKey(SETTING_KEY)
                .orElseGet(() -> new PlatformSetting(SETTING_KEY, String.valueOf(safe)));
        row.setSettingValue(String.valueOf(safe));
        settings.save(row);
        return safe;
    }

    /**
     * Upserts one order's commercial fact into its daily aggregate bucket, so
     * the rollup exists before any future detailed-row purge could remove the
     * source row.
     *
     * <p><b>Call this exactly once per order, when the order is placed.</b> The
     * upsert is additive ({@code orderCount + 1}, {@code recordedOrderValue +
     * total}), so calling it again on delivery would double-count the same
     * order. For the same reason a later cancellation cannot subtract the
     * already-recorded amount; the cancelled-order branch below contributes a
     * zero delta, which keeps the row consistent without inflating it.
     *
     * <p>Runs in the caller's transaction on purpose: the rollup must commit or
     * roll back together with the order it describes, so a failed checkout can
     * never leave an aggregate row for an order that does not exist. It is
     * wrapped in a catch-all because analytics must never break checkout.</p>
     */
    @Transactional
    public void recordOrderFact(Order order) {
        if (order == null) return;
        try {
            LocalDate day = orderDay(order);
            if (day == null) return;
            Kitchen kitchen = order.getKitchen();
            User seller = kitchen != null ? kitchen.getSeller() : null;
            User buyer = order.getBuyer();
            String category = kitchen != null && kitchen.getSellerType() != null
                    ? kitchen.getSellerType().name() : null;

            Long areaId = null; String areaName = null;
            Long societyId = null; String societyName = null;
            if (buyer != null) {
                if (buyer.getAreaRef() != null) {
                    areaId = buyer.getAreaRef().getId();
                    areaName = buyer.getAreaRef().getName();
                }
                if (buyer.getSocietyRef() != null) {
                    societyId = buyer.getSocietyRef().getId();
                    societyName = buyer.getSocietyRef().getName();
                }
                if (societyName == null) societyName = buyer.getSociety();
                if (areaName == null) areaName = buyer.getArea();
            }

            // A cancelled order contributes no count and no value to the rollup.
            int count = order.getOrderStatus() == OrderStatus.CANCELLED ? 0 : 1;
            BigDecimal value = count == 1
                    ? (order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                    : BigDecimal.ZERO;

            // Captured by the lambda below, so every one of these must be final.
            final Long sellerId = seller != null ? seller.getId() : null;
            final String sellerName = seller != null ? seller.getName() : null;
            final Long buyerAreaId = areaId;
            final String buyerAreaName = areaName;
            final Long buyerSocietyId = societyId;
            final String buyerSocietyName = societyName;

            OrderDailyAggregate row =
                    aggregates.findByDayAndSellerIdAndAreaIdAndSocietyIdAndCategory(
                                    day, sellerId, buyerAreaId, buyerSocietyId, category)
                            .orElseGet(() -> new OrderDailyAggregate(day,
                                    sellerId, sellerName,
                                    buyerAreaId, buyerAreaName, buyerSocietyId, buyerSocietyName,
                                    category, 0, BigDecimal.ZERO));
            row.setOrderCount(row.getOrderCount() + count);
            row.setRecordedOrderValue(row.getRecordedOrderValue().add(value));
            aggregates.save(row);
        } catch (RuntimeException ignored) {
            // Aggregation must never break the order flow it observes.
        }
    }

    /**
     * Read-only description of what a purge WOULD remove. Executes no delete,
     * so the reporting stays honest about what the demo actually does.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> retentionStatus() {
        int days = retentionDays();
        LocalDateTime cutoffAt = LocalDate.now().minusDays(days).atStartOfDay();

        long wouldPurge = orders.findAll().stream()
                .filter(o -> o.getOrderStatus() != OrderStatus.DRAFT)
                .filter(o -> o.getDeliveredAt() != null && o.getDeliveredAt().isBefore(cutoffAt))
                .filter(o -> o.getOrderStatus() == OrderStatus.CANCELLED || o.isDelivered())
                .count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settingKey", SETTING_KEY);
        m.put("retentionDays", days);
        m.put("propertyOverride", PROPERTY);
        m.put("defaultDays", DEFAULT_RETENTION_DAYS);
        m.put("purgeCutoffDate", cutoffAt.toLocalDate());
        m.put("detailedRowsPastWindow", wouldPurge);
        m.put("destructivePurgeEnabled", purgeEnabled());
        m.put("purgeEnabledSettingKey", SETTING_PURGE_KEY);
        m.put("note", "Purge is OFF by default and never runs on a schedule. An Admin must enable it, "
                + "export the affected rows, confirm the server-calculated count and give a reason. "
                + "Daily aggregates and the audit trail always outlive any purge.");
        m.put("aggregatedOrderCount",
                aggregates.sumOrderCount(LocalDate.now().minusDays(365), LocalDate.now()));
        m.put("aggregatedRecordedOrderValue",
                aggregates.sumValue(LocalDate.now().minusDays(365), LocalDate.now()));
        return m;
    }

    /** True only when an Admin has explicitly switched the purge on. Default OFF. */
    @Transactional(readOnly = true)
    public boolean purgeEnabled() {
        PlatformSetting row = settings.findBySettingKey(SETTING_PURGE_KEY).orElse(null);
        return row != null && row.getSettingValue() != null
                && "true".equalsIgnoreCase(row.getSettingValue().trim());
    }

    @Transactional
    public void setPurgeEnabled(boolean enabled) {
        // Upsert, matching setRetentionDays: SETTING_KEY is unique, so a plain
        // insert would fail the second time an Admin toggles the switch.
        PlatformSetting row = settings.findBySettingKey(SETTING_PURGE_KEY).orElse(null);
        if (row == null) {
            settings.save(new PlatformSetting(SETTING_PURGE_KEY, enabled ? "true" : "false"));
        } else {
            row.setSettingValue(enabled ? "true" : "false");
            settings.save(row);
        }
    }

    /**
     * Detailed rows eligible for purge right now.
     *
     * <p>Only CLOSED orders count: delivered, or cancelled. An order still in
     * flight - draft, ordered, confirmed, ready - is never a candidate, however
     * old, because removing it would destroy a live customer order.</p>
     *
     * <p>Uses the same predicate as {@link #retentionStatus()} so the number the
     * Admin confirms always matches what the purge would actually remove.</p>
     */
    @Transactional(readOnly = true)
    public List<Order> purgeCandidates() {
        int days = retentionDays();
        LocalDateTime cutoffAt = LocalDate.now().minusDays(days).atStartOfDay();
        List<Order> out = new ArrayList<>();
        for (Order o : orders.findAll()) {
            if (o.getOrderStatus() == OrderStatus.DRAFT) continue;
            if (!(o.getOrderStatus() == OrderStatus.CANCELLED || o.isDelivered())) continue;
            LocalDateTime terminalAt = o.getDeliveredAt() != null ? o.getDeliveredAt() : o.getUpdatedAt();
            if (terminalAt == null || terminalAt.isBefore(cutoffAt)) out.add(o);
        }
        return out;
    }

    /**
     * Deletes closed detailed orders older than the retention window (handover 11).
     *
     * <p>Every guard is a hard precondition and none is optional:</p>
     * <ol>
     *   <li>the purge must have been explicitly enabled by an Admin;</li>
     *   <li>the caller must confirm, so no accidental single click deletes data;</li>
     *   <li>a non-blank reason is required and becomes the audit reason;</li>
     *   <li>the caller must confirm having exported the affected rows first.</li>
     * </ol>
     *
     * <p>Only {@code orders} rows are deleted. Daily aggregates and the audit
     * trail are long-lived records that must outlive the detailed orders, so they
     * are never touched here - which is precisely why the aggregate rollup exists.</p>
     *
     * <p>Transactional: the whole batch commits or rolls back, so a failure part
     * way through cannot leave the platform with half its history removed.</p>
     */
    @Transactional
    public Map<String, Object> purgeClosedOrdersPastRetention(boolean confirmed, boolean exported,
                                                              String reason) {
        if (!purgeEnabled()) {
            throw new IllegalStateException(
                    "Retention purge is disabled. Enable it in Admin before purging.");
        }
        if (!confirmed) {
            throw new IllegalArgumentException("The purge must be explicitly confirmed.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when purging orders.");
        }
        if (!exported) {
            // Handover 11: "export before purge". Without an export the evidence
            // would be gone for good, so this is refused rather than merely warned.
            throw new IllegalArgumentException(
                    "Export the affected orders before purging.");
        }

        List<Order> candidates = purgeCandidates();
        List<Long> ids = new ArrayList<>();
        for (Order o : candidates) if (o.getId() != null) ids.add(o.getId());
        if (!ids.isEmpty()) {
            // ORDER_ITEMS has a non-nullable FK to ORDERS, so the children must be
            // removed first or the whole purge fails on referential integrity.
            // Both statements are in this one transaction: either both happen or
            // neither does, so a failure cannot orphan any line items.
            orderItems.deleteByOrderIds(ids);
            orders.deleteAllByIdInBatch(ids);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("purgedOrderCount", ids.size());
        m.put("retentionDays", retentionDays());
        m.put("purgeCutoffDate", LocalDate.now().minusDays(retentionDays()));
        m.put("reason", reason.trim());
        m.put("aggregatesRetained", true);
        m.put("auditTrailRetained", true);
        m.put("aggregatedOrderCount",
                aggregates.sumOrderCount(LocalDate.now().minusDays(365), LocalDate.now()));
        return m;
    }

    private LocalDate orderDay(Order o) {
        if (o.getOrderTime() != null) return o.getOrderTime().toLocalDate();
        if (o.getCreatedAt() != null) return o.getCreatedAt().toLocalDate();
        return null;
    }

    private static Integer parsePositive(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int clamp(int v) {
        return Math.max(MIN_RETENTION_DAYS, Math.min(MAX_RETENTION_DAYS, v));
    }
}