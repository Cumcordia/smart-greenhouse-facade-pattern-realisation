package greenhouse.c;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Шлюз подсистемы C к SQLite. Таблицы: dosages (дозы удобрения) и alerts (оповещения).
 * Соединение открывается на каждую операцию, поэтому «рестарт» — это просто
 * новый DosageRepository на тот же файл.
 */
public class DosageRepository {

    /** Ошибка хранилища (не бизнес-ошибка). */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage(), cause);
        }
    }

    private final String url;

    public DosageRepository(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        init();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void init() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS dosages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "bed_id TEXT NOT NULL, "
                    + "crop TEXT NOT NULL, "
                    + "water_liters REAL NOT NULL, "
                    + "grams REAL NOT NULL, "
                    + "status TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS alerts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "message TEXT NOT NULL, "
                    + "status TEXT NOT NULL)");
        } catch (SQLException e) {
            throw new StorageException("Cannot initialise subsystem C storage", e);
        }
    }

    // ---------- dosages ----------

    /** Вставляет новую дозу (id == null) или обновляет статус существующей. */
    public void save(DosageRecord record) {
        try (Connection c = open()) {
            if (record.getId() == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dosages(bed_id, crop, water_liters, grams, status) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, record.getBedId());
                    ps.setString(2, record.getCrop().name());
                    ps.setDouble(3, record.getWaterLiters());
                    ps.setDouble(4, record.getGrams());
                    ps.setString(5, record.getStatus().name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        record.assignId(keys.getLong(1));
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE dosages SET status = ? WHERE id = ?")) {
                    ps.setString(1, record.getStatus().name());
                    ps.setLong(2, record.getId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save dosage", e);
        }
    }

    public Optional<DosageRecord> findById(long id) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, bed_id, crop, water_liters, grams, status FROM dosages WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapDosage(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read dosage " + id, e);
        }
    }

    public List<DosageRecord> findAll() {
        List<DosageRecord> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT id, bed_id, crop, water_liters, grams, status FROM dosages ORDER BY id")) {
            while (rs.next()) {
                result.add(mapDosage(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot list dosages", e);
        }
        return result;
    }

    private DosageRecord mapDosage(ResultSet rs) throws SQLException {
        return DosageRecord.restore(
                rs.getLong("id"),
                rs.getString("bed_id"),
                DosageCalculator.Crop.valueOf(rs.getString("crop")),
                rs.getDouble("water_liters"),
                rs.getDouble("grams"),
                DosageRecord.Status.valueOf(rs.getString("status")));
    }

    // ---------- alerts ----------

    /** Вставляет оповещение и возвращает его id. */
    public long insertAlert(String message, AlertService.Status status) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO alerts(message, status) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, message);
            ps.setString(2, status.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save alert", e);
        }
    }

    public void updateAlertStatus(long id, AlertService.Status status) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("UPDATE alerts SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot update alert " + id, e);
        }
    }

    public Optional<AlertService.Alert> findAlert(long id) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT id, message, status FROM alerts WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapAlert(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read alert " + id, e);
        }
    }

    public List<AlertService.Alert> findAllAlerts() {
        List<AlertService.Alert> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, message, status FROM alerts ORDER BY id")) {
            while (rs.next()) {
                result.add(mapAlert(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot list alerts", e);
        }
        return result;
    }

    private AlertService.Alert mapAlert(ResultSet rs) throws SQLException {
        return new AlertService.Alert(
                rs.getLong("id"),
                rs.getString("message"),
                AlertService.Status.valueOf(rs.getString("status")));
    }

    /** Полный сброс хранилища C (для CLI и тестов). */
    public void clear() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM dosages");
            s.execute("DELETE FROM alerts");
            s.execute("DELETE FROM sqlite_sequence WHERE name IN ('dosages', 'alerts')");
        } catch (SQLException e) {
            throw new StorageException("Cannot clear subsystem C storage", e);
        }
    }
}
