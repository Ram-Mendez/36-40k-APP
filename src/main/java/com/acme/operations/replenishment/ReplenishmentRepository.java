package com.acme.operations.replenishment;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageRequests;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Repository
public class ReplenishmentRepository {
    private static final String POLICY_FROM = """
             FROM replenishment_policy r
             JOIN product p ON p.id=r.product_id
             LEFT JOIN supplier s ON s.id=r.preferred_supplier_id
            """;
    private static final String POLICY_COLUMNS = """
            SELECT r.product_id,p.sku,p.name AS product_name,p.active AS product_active,
                   r.minimum_stock,r.target_stock,r.preferred_supplier_id,
                   s.name AS supplier_name,s.active AS supplier_active,r.active,r.version,
                   r.created_at,r.updated_at
            """;
    // Aggregate pending purchases before joining product balances, so each product appears once.
    private static final String PROPOSAL_CTE = """
            WITH pending AS (
                SELECT l.product_id, SUM(l.quantity-l.received_quantity) AS incoming
                FROM purchase_order_line l
                JOIN purchase_order o ON o.id=l.purchase_order_id
                WHERE o.status IN ('SUBMITTED','PARTIALLY_RECEIVED')
                GROUP BY l.product_id
            ), positions AS (
                SELECT r.product_id,p.sku,p.name AS product_name,
                       r.minimum_stock,r.target_stock,r.preferred_supplier_id,
                       s.name AS supplier_name,s.active AS supplier_active,r.version AS policy_version,
                       COALESCE(b.on_hand,0)-COALESCE(b.reserved,0) AS available,
                       COALESCE(i.incoming,0) AS incoming,
                       COALESCE(b.on_hand,0)-COALESCE(b.reserved,0)+COALESCE(i.incoming,0) AS projected
                FROM replenishment_policy r
                JOIN product p ON p.id=r.product_id
                LEFT JOIN inventory_balance b ON b.product_id=p.id
                LEFT JOIN pending i ON i.product_id=p.id
                LEFT JOIN supplier s ON s.id=r.preferred_supplier_id
                WHERE r.active AND p.active
            )
            """;

    private static final RowMapper<ReplenishmentDtos.Policy> POLICY = (rs, row) ->
            new ReplenishmentDtos.Policy(
                    rs.getLong("product_id"),rs.getString("sku"),rs.getString("product_name"),
                    rs.getBoolean("product_active"),rs.getLong("minimum_stock"),rs.getLong("target_stock"),
                    rs.getObject("preferred_supplier_id",Long.class),rs.getString("supplier_name"),
                    rs.getObject("supplier_active",Boolean.class),rs.getBoolean("active"),rs.getLong("version"),
                    rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<ReplenishmentDtos.Proposal> PROPOSAL = (rs, row) -> {
        Long supplier = rs.getObject("preferred_supplier_id",Long.class);
        Boolean supplierActive = rs.getObject("supplier_active",Boolean.class);
        boolean purchasable = supplier != null && Boolean.TRUE.equals(supplierActive);
        String blockingReason = supplier == null ? "NO_PREFERRED_SUPPLIER"
                : !Boolean.TRUE.equals(supplierActive) ? "SUPPLIER_INACTIVE" : null;
        return new ReplenishmentDtos.Proposal(
                rs.getLong("product_id"),rs.getString("sku"),rs.getString("product_name"),
                rs.getLong("minimum_stock"),rs.getLong("target_stock"),rs.getLong("available"),
                rs.getLong("incoming"),rs.getLong("projected"),rs.getLong("suggested_quantity"),
                supplier,rs.getString("supplier_name"),supplierActive,purchasable,blockingReason,
                rs.getLong("policy_version"));
    };

    private final JdbcTemplate jdbc;

    public ReplenishmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean productActive(long productId) {
        return jdbc.query("SELECT active FROM product WHERE id=?", (rs, row) -> rs.getBoolean(1), productId)
                .stream().findFirst().orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","Product not found"));
    }

    public void requireActiveSupplier(long supplierId) {
        var active = jdbc.query("SELECT active FROM supplier WHERE id=? FOR SHARE",
                (rs, row) -> rs.getBoolean(1),supplierId).stream().findFirst().orElseThrow(
                        () -> new BusinessException(HttpStatus.NOT_FOUND,"SUPPLIER_NOT_FOUND","Supplier not found"));
        if (!active) throw new BusinessException(
                HttpStatus.CONFLICT,"SUPPLIER_UNAVAILABLE","Preferred supplier must be active when assigned");
    }

    public Optional<ReplenishmentDtos.Policy> find(long productId) {
        return jdbc.query(POLICY_COLUMNS + POLICY_FROM + " WHERE r.product_id=?",POLICY,productId)
                .stream().findFirst();
    }

    public boolean create(long productId, ReplenishmentDtos.PolicyInput input) {
        return jdbc.update("""
                INSERT INTO replenishment_policy(product_id,minimum_stock,target_stock,preferred_supplier_id,active)
                VALUES (?,?,?,?,?) ON CONFLICT (product_id) DO NOTHING
                """, productId,input.minimumStock(),input.targetStock(),input.preferredSupplierId(),input.active()) == 1;
    }

    public boolean replace(long productId, ReplenishmentDtos.PolicyInput input) {
        return jdbc.update("""
                UPDATE replenishment_policy
                SET minimum_stock=?,target_stock=?,preferred_supplier_id=?,active=?,version=version+1,updated_at=now()
                WHERE product_id=? AND version=?
                """, input.minimumStock(),input.targetStock(),input.preferredSupplierId(),input.active(),
                productId,input.version()) == 1;
    }

    public PageResponse<ReplenishmentDtos.Policy> policies(
            Long productId, Long supplierId, Boolean active, String search, int page, int size) {
        var request = PageRequests.of(page,size,"id","asc",Set.of("id"));
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        filter(where,args,"r.product_id",productId);
        filter(where,args,"r.preferred_supplier_id",supplierId);
        filter(where,args,"r.active",active);
        search(where,args,"p.sku","p.name",search);
        Long count = jdbc.queryForObject("SELECT count(*)"+POLICY_FROM+where,Long.class,args.toArray());
        args.add(size);
        args.add(request.getOffset());
        var rows = jdbc.query(POLICY_COLUMNS+POLICY_FROM+where+" ORDER BY r.product_id LIMIT ? OFFSET ?",
                POLICY,args.toArray());
        return new PageResponse<>(rows,page,size,count,(int)((count+size-1)/size));
    }

    public PageResponse<ReplenishmentDtos.Proposal> proposals(
            Long productId, Long supplierId, String search, int page, int size) {
        var request = PageRequests.of(page,size,"id","asc",Set.of("id"));
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE projected < minimum_stock");
        filter(where,args,"product_id",productId);
        filter(where,args,"preferred_supplier_id",supplierId);
        search(where,args,"sku","product_name",search);
        Long count = jdbc.queryForObject(PROPOSAL_CTE+"SELECT count(*) FROM positions"+where,
                Long.class,args.toArray());
        args.add(size);
        args.add(request.getOffset());
        var rows = jdbc.query(PROPOSAL_CTE+"""
                SELECT positions.*,GREATEST(0,target_stock-projected) AS suggested_quantity
                FROM positions
                """+where+" ORDER BY product_id LIMIT ? OFFSET ?",PROPOSAL,args.toArray());
        return new PageResponse<>(rows,page,size,count,(int)((count+size-1)/size));
    }

    private static void filter(StringBuilder where, ArrayList<Object> args, String column, Object value) {
        if (value != null) {
            where.append(" AND ").append(column).append("=?");
            args.add(value);
        }
    }

    private static void search(
            StringBuilder where, ArrayList<Object> args, String sku, String name, String search) {
        if (search == null || search.isBlank()) return;
        if (search.length() > 200) throw new BusinessException(
                HttpStatus.BAD_REQUEST,"INVALID_SEARCH","Search is limited to 200 characters");
        String pattern = "%"+search.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%")
                .replace("_","!_")+"%";
        where.append(" AND (lower(").append(sku).append(") LIKE ? ESCAPE '!' OR lower(")
                .append(name).append(") LIKE ? ESCAPE '!')");
        args.add(pattern);
        args.add(pattern);
    }
}
