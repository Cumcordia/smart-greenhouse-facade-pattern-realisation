package greenhouse.facade;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Хранение хода цепочки на диске (таблица cycles в SQLite) — для восстановления после рестарта.
 * Соединение открывается на каждую операцию, поэтому «рестарт» — это новый CycleRepository
 * на тот же файл.
 */
public class CycleRepository {

    /** Ошибка хранилища (не бизнес-ошибка). */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage(), cause);
        }
    }

    private static final String COLUMNS =
            "id, bed_id, crop, temperature, humidity, status, slot_id, run_id, dosage_id, alert_id, failure_reason";

    private final String url;

    public CycleRepository(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        init();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void init() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS cycles ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "bed_id TEXT NOT NULL, "
                    + "crop TEXT NOT NULL, "
                    + "temperature REAL NOT NULL, "
                    + "humidity REAL NOT NULL, "
                    + "status TEXT NOT NULL, "
                    + "slot_id INTEGER NOT NULL, "
                    + "run_id INTEGER, "
                    + "dosage_id INTEGER, "
                    + "alert_id INTEGER, "
                    + "failure_reason TEXT)");
        } catch (SQLException e) {
            throw new StorageException("Cannot initialise cycle storage", e);
        }
    }

    /** Вставляет новую запись (id == null) или обновляет изменяемые поля существующей. */
    public void save(CycleRecord cycle) {
        try (Connection c = open()) {
            if (cycle.getId() == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO cycles(bed_id, crop, temperature, humidity, status, slot_id, "
                                + "run_id, dosage_id, alert_id, failure_reason) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, cycle.getBedId());
                    ps.setString(2, cycle.getCrop());
                    ps.setDouble(3, cycle.getTemperatureC());
                    ps.setDouble(4, cycle.getHumidityPercent());
                    ps.setString(5, cycle.getStatus().name());
                    ps.setLong(6, cycle.getSlotId());
                    setNullableLong(ps, 7, cycle.getRunId());
                    setNullableLong(ps, 8, cycle.getDosageId());
                    setNullableLong(ps, 9, cycle.getAlertId());
                    setNullableString(ps, 10, cycle.getFailureReason());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        cycle.assignId(keys.getLong(1));
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE cycles SET status = ?, run_id = ?, dosage_id = ?, alert_id = ?, "
                                + "failure_reason = ? WHERE id = ?")) {
                    ps.setString(1, cycle.getStatus().name());
                    setNullableLong(ps, 2, cycle.getRunId());
                    setNullableLong(ps, 3, cycle.getDosageId());
                    setNullableLong(ps, 4, cycle.getAlertId());
                    setNullableString(ps, 5, cycle.getFailureReason());
                    ps.setLong(6, cycle.getId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save cycle", e);
        }
    }

    public Optional<CycleRecord> findById(long id) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM cycles WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read cycle " + id, e);
        }
    }

    public List<CycleRecord> findAll() {
        return query("SELECT " + COLUMNS + " FROM cycles ORDER BY id");
    }

    /** Незавершённые цепочки (PENDING_B / PENDING_C) — то, что оборвалось и ждёт решения. */
    public List<CycleRecord> findInFlight() {
        return query("SELECT " + COLUMNS + " FROM cycles WHERE status IN ('PENDING_B', 'PENDING_C') ORDER BY id");
    }

    private List<CycleRecord> query(String sql) {
        List<CycleRecord> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot list cycles", e);
        }
        return result;
    }

    private CycleRecord map(ResultSet rs) throws SQLException {
        return CycleRecord.restore(
                rs.getLong("id"),
                rs.getString("bed_id"),
                rs.getString("crop"),
                rs.getDouble("temperature"),
                rs.getDouble("humidity"),
                CycleRecord.Status.valueOf(rs.getString("status")),
                rs.getLong("slot_id"),
                nullableLong(rs, "run_id"),
                nullableLong(rs, "dosage_id"),
                nullableLong(rs, "alert_id"),
                rs.getString("failure_reason"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    /** Полный сброс хода цепочек (для CLI и тестов). */
    public void clear() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM cycles");
            s.execute("DELETE FROM sqlite_sequence WHERE name = 'cycles'");
        } catch (SQLException e) {
            throw new StorageException("Cannot clear cycle storage", e);
        }
    }
}
