package greenhouse.c;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SlotRepository {

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(cause == null || cause.getMessage() == null ? message : message + ": " + cause.getMessage(), cause);
        }
    }

    private final String url;

    public SlotRepository(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        init();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void init() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS slots ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "bed_id TEXT NOT NULL, "
                    + "liters REAL NOT NULL, "
                    + "status TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS soil_readings ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "bed_id TEXT NOT NULL, "
                    + "moisture REAL NOT NULL, "
                    + "taken_at TEXT NOT NULL)");
        } catch (SQLException e) {
            throw new StorageException("Cannot initialise subsystem A storage", e);
        }
    }

    public void save(IrrigationSlot slot) {
        try (Connection c = open()) {
            if (slot.getId() == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO slots(bed_id, liters, status) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, slot.getBedId());
                    ps.setDouble(2, slot.getLiters());
                    ps.setString(3, slot.getStatus().name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        slot.assignId(keys.getLong(1));
                    }
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE slots SET status = ? WHERE id = ?")) {
                    ps.setString(1, slot.getStatus().name());
                    ps.setLong(2, slot.getId());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot save slot", e);
        }
    }

    public Optional<IrrigationSlot> findById(long id) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, bed_id, liters, status FROM slots WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read slot " + id, e);
        }
    }

    public Optional<IrrigationSlot> findActiveByBed(String bedId) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, bed_id, liters, status FROM slots "
                             + "WHERE bed_id = ? AND status = 'RESERVED' "
                             + "ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, bedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read active slot for " + bedId, e);
        }
    }

    public List<IrrigationSlot> findAll() {
        List<IrrigationSlot> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, bed_id, liters, status FROM slots ORDER BY id")) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot list slots", e);
        }
        return result;
    }

    private IrrigationSlot map(ResultSet rs) throws SQLException {
        return IrrigationSlot.restore(
                rs.getLong("id"),
                rs.getString("bed_id"),
                rs.getDouble("liters"),
                IrrigationSlot.Status.valueOf(rs.getString("status")));
    }

    // ---------- soil readings ----------

    public void saveReading(String bedId, double moisturePercent) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO soil_readings(bed_id, moisture, taken_at) VALUES (?, ?, ?)")) {
            ps.setString(1, bedId);
            ps.setDouble(2, moisturePercent);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Cannot save soil reading", e);
        }
    }

    public Optional<Double> latestMoisture(String bedId) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT moisture FROM soil_readings WHERE bed_id = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, bedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getDouble(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Cannot read soil moisture for " + bedId, e);
        }
    }

    public void clear() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM slots");
            s.execute("DELETE FROM soil_readings");
            s.execute("DELETE FROM sqlite_sequence WHERE name IN ('slots', 'soil_readings')");
        } catch (SQLException e) {
            throw new StorageException("Cannot clear subsystem A storage", e);
        }
    }
}
