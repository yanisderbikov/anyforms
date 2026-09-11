package ru.anyforms.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.anyforms.dto.cdek.CdekPvzDTO;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CdekPvzJdbcRepo {

    private static final int BATCH_SIZE = 1000;
    private static final String SELECT_ALL = """
            SELECT code, name, country_code, region, city, postal_code, address, address_full, work_time, longitude, latitude
            FROM cdek_pvz
            """;
    private static final String INSERT = """
            INSERT INTO cdek_pvz (code, name, country_code, region, city, postal_code, address, address_full, work_time, longitude, latitude, synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (code) DO NOTHING
            """;
    private static final RowMapper<CdekPvzDTO> ROW_MAPPER = CdekPvzJdbcRepo::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public List<CdekPvzDTO> findAll() {
        return jdbcTemplate.query(SELECT_ALL, ROW_MAPPER);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cdek_pvz", Long.class);
        return count == null ? 0 : count;
    }

    @Transactional
    public void replaceAll(List<CdekPvzDTO> points) {
        Timestamp syncedAt = Timestamp.from(Instant.now());
        jdbcTemplate.update("DELETE FROM cdek_pvz");
        jdbcTemplate.batchUpdate(INSERT, points, BATCH_SIZE, (ps, p) -> {
            ps.setString(1, p.getCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getCountryCode());
            ps.setString(4, p.getRegion());
            ps.setString(5, p.getCity());
            ps.setString(6, p.getPostalCode());
            ps.setString(7, p.getAddress());
            ps.setString(8, p.getFullAddress());
            ps.setString(9, p.getWorkTime());
            setDouble(ps, 10, p.getLongitude());
            setDouble(ps, 11, p.getLatitude());
            ps.setTimestamp(12, syncedAt);
        });
    }

    private static void setDouble(java.sql.PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static CdekPvzDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        return CdekPvzDTO.builder()
                .code(rs.getString("code"))
                .name(rs.getString("name"))
                .countryCode(rs.getString("country_code"))
                .region(rs.getString("region"))
                .city(rs.getString("city"))
                .postalCode(rs.getString("postal_code"))
                .address(rs.getString("address"))
                .fullAddress(rs.getString("address_full"))
                .workTime(rs.getString("work_time"))
                .longitude(rs.getObject("longitude", Double.class))
                .latitude(rs.getObject("latitude", Double.class))
                .build();
    }
}
