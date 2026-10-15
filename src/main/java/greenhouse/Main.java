package greenhouse;

import greenhouse.a.IrrigationPolicy;
import greenhouse.a.IrrigationScheduler;
import greenhouse.a.IrrigationSlot;
import greenhouse.a.SlotRepository;
import greenhouse.a.SoilSensorArray;
import greenhouse.b.IrrigationController;
import greenhouse.b.IrrigationRun;
import greenhouse.b.RunRepository;
import greenhouse.b.VentilationPolicy;
import greenhouse.b.VentilationUnit;
import greenhouse.c.AlertService;
import greenhouse.c.DosageCalculator;
import greenhouse.c.DosageRecord;
import greenhouse.c.DosageRepository;
import greenhouse.c.NutrientDispenser;
import greenhouse.facade.CycleRecord;
import greenhouse.facade.CycleRepository;
import greenhouse.facade.GreenhouseAdminFacade;
import greenhouse.facade.GreenhouseFacade;
import greenhouse.facade.GreenhouseAdminFacade.SystemState;
import greenhouse.facade.GreenhouseFacade.CycleRequest;
import greenhouse.facade.exceptions.GreenhouseException;
import greenhouse.facade.exceptions.InvalidInputException;
import greenhouse.facade.exceptions.IrrigationFailedException;
import greenhouse.facade.exceptions.NutrientFailedException;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class Main {

    private static class InputClosed extends RuntimeException {}

    private final String dbPath;
    private final Scanner in;
    private final PrintStream out;

    private final SoilSensorArray sensors;
    private final IrrigationScheduler scheduler;
    private final IrrigationController irrigation;
    private final VentilationUnit ventilation;
    private final NutrientDispenser dispenser;
    private final AlertService alerts;
    private final CycleRepository cycles;

    private final GreenhouseFacade facade;
    private final GreenhouseAdminFacade admin;

    private boolean pumpFaulty;
    private boolean dispenserFaulty;

    public Main(String dbPath, InputStream input, PrintStream output) {
        this.dbPath = dbPath;
        this.in = new Scanner(input);
        this.out = output;

        SlotRepository slots = new SlotRepository(dbPath);
        this.sensors = new SoilSensorArray(slots);
        this.scheduler = new IrrigationScheduler(slots, sensors, new IrrigationPolicy());

        RunRepository runs = new RunRepository(dbPath);
        this.irrigation = new IrrigationController(runs);
        this.ventilation = new VentilationUnit(runs, new VentilationPolicy());

        DosageRepository dosages = new DosageRepository(dbPath);
        this.dispenser = new NutrientDispenser(dosages, new DosageCalculator());
        this.alerts = new AlertService(dosages);

        this.cycles = new CycleRepository(dbPath);
        this.facade = new GreenhouseFacade(sensors, scheduler, irrigation, ventilation,
                dispenser, alerts, cycles);
        this.admin = new GreenhouseAdminFacade(facade, cycles, slots, runs, dosages, irrigation, dispenser);
    }

    public static void main(String[] args) {
        String dbPath = args.length > 0 ? args[0] : "greenhouse.db";
        new Main(dbPath, System.in, System.out).run();
    }

    public void run() {
        out.println("=== Smart Greenhouse ===   database file: " + dbPath);
        try {
            offerRecovery();
            while (true) {
                printMenu();
                String choice = ask("Choose", null);
                try {
                    switch (choice) {
                        case "1" -> runCycle();
                        case "2" -> printState();
                        case "3" -> togglePump();
                        case "4" -> toggleDispenser();
                        case "5" -> compensateManually();
                        case "6" -> recover();
                        case "7" -> simulateCrash();
                        case "8" -> reset();
                        case "0" -> {
                            out.println("Bye.");
                            return;
                        }
                        default -> out.println("Unknown option '" + choice + "'.");
                    }
                } catch (InvalidInputException e) {
                    out.println("REJECTED (nothing was committed): " + e.getMessage());
                } catch (GreenhouseException e) {
                    out.println("ERROR: " + e.getMessage());
                } catch (InputClosed e) {
                    throw e;
                } catch (RuntimeException e) {
                    out.println("ERROR: " + e.getMessage());
                }
            }
        } catch (InputClosed e) {
            out.println();
            out.println("Input closed. Bye.");
        }
    }

    private void printMenu() {
        out.println();
        out.println("----------------------------------------------");
        out.println(" 1) Run full cycle  A -> B -> C");
        out.println(" 2) Show persisted state (read from disk)");
        out.println(" 3) Toggle pump failure  [step B]       now: " + (pumpFaulty ? "FAULTY" : "ok"));
        out.println(" 4) Toggle dispenser failure  [step C]  now: " + (dispenserFaulty ? "FAULTY" : "ok"));
        out.println(" 5) Compensate a cycle manually");
        out.println(" 6) Recover interrupted cycles");
        out.println(" 7) Simulate a crash in the middle of the chain");
        out.println(" 8) Reset storage");
        out.println(" 0) Exit");
        out.println("----------------------------------------------");
    }

    private void runCycle() {
        CycleRequest request = readRequest();
        try {
            CycleRecord cycle = facade.runCycle(request);
            out.println("SUCCESS: " + cycle);
        } catch (IrrigationFailedException e) {
            out.println("STEP B FAILED: " + e.getMessage());
            printCycle(e.getCycleId());
        } catch (NutrientFailedException e) {
            out.println("STEP C FAILED: " + e.getMessage());
            printCycle(e.getCycleId());
        }
    }

    private void togglePump() {
        pumpFaulty = !pumpFaulty;
        admin.simulatePumpFailure(pumpFaulty);
        out.println("Pump is now " + (pumpFaulty
                ? "FAULTY: the next cycle fails at step B (only A is rolled back)."
                : "working."));
    }

    private void toggleDispenser() {
        dispenserFaulty = !dispenserFaulty;
        admin.simulateDispenserFailure(dispenserFaulty);
        out.println("Dispenser is now " + (dispenserFaulty
                ? "FAULTY: the next cycle fails at step C (B and then A are rolled back)."
                : "working."));
    }

    private void compensateManually() {
        long id = askLong("Cycle id to compensate");
        CycleRecord cycle = admin.compensate(id);
        out.println("Compensated: " + cycle);
    }

    private void recover() {
        List<CycleRecord> recovered = facade.recoverInterrupted();
        if (recovered.isEmpty()) {
            out.println("Nothing to recover: no unfinished cycles on disk.");
            return;
        }
        out.println("Recovered " + recovered.size() + " interrupted cycle(s):");
        recovered.forEach(c -> out.println("  " + c));
    }

    private void reset() {
        if (!confirm("Wipe ALL persisted data (slots, runs, doses, alerts, cycles)?")) {
            out.println("Cancelled.");
            return;
        }
        admin.resetAll();
        pumpFaulty = false;
        dispenserFaulty = false;
        out.println("Storage is clean, simulated failures are switched off.");
    }

    private void simulateCrash() {
        out.println("1) crash right after step B (slot reserved, pump running, ventilation on)");
        out.println("2) crash during step C (dose dispensed, alert created, cycle not finished)");
        boolean withDosage = switch (ask("Choose", "1")) {
            case "1" -> false;
            case "2" -> true;
            default -> throw new IllegalArgumentException("Choose 1 or 2");
        };
        CycleRequest r = readRequest();
        String crop = DosageCalculator.Crop.fromName(r.crop()).name();

        sensors.recordMoisture(r.bedId(), r.moisturePercent());
        IrrigationSlot slot = scheduler.reserve(r.bedId());
        CycleRecord record = CycleRecord.begin(r.bedId(), crop, r.temperatureC(), r.humidityPercent(), slot.getId());
        cycles.save(record);

        IrrigationRun run = irrigation.start(r.bedId(), slot.getLiters());
        record.attachRun(run.getId());
        cycles.save(record);
        ventilation.engage(r.temperatureC(), r.humidityPercent());
        record.advanceToPendingC();
        cycles.save(record);

        if (withDosage) {
            DosageRecord dosage = dispenser.dispense(r.bedId(), slot.getLiters(), crop);
            record.attachDosage(dosage.getId());
            cycles.save(record);
            record.attachAlert(alerts.create("Fertilizer dispensed for " + r.bedId()).id());
            cycles.save(record);
        }
        out.println("The application \"crashed\": " + record);
        out.println("Use option 6 now, or exit and start the program again to see recovery on startup.");
    }

    private void offerRecovery() {
        List<CycleRecord> inFlight = admin.listCycles().stream().filter(CycleRecord::isInFlight).toList();
        if (inFlight.isEmpty()) {
            return;
        }
        out.println();
        out.println("Found " + inFlight.size() + " unfinished cycle(s) left on disk from a previous run:");
        inFlight.forEach(c -> out.println("  " + c));
        if (confirm("Roll them back now?")) {
            recover();
        } else {
            out.println("Left as is. Use option 6 or 5 later.");
        }
    }

    private void printState() {
        SystemState s = admin.inspect();

        out.println();
        out.println("--- Cycles (chain progress) ---");
        if (s.cycles().isEmpty()) {
            out.println("  (none)");
        }
        for (CycleRecord c : s.cycles()) {
            out.println("  #" + c.getId() + "  bed=" + c.getBedId() + "  crop=" + c.getCrop()
                    + "  status=" + c.getStatus()
                    + "  slot=" + c.getSlotId() + "  run=" + dash(c.getRunId())
                    + "  dosage=" + dash(c.getDosageId()) + "  alert=" + dash(c.getAlertId())
                    + (c.getFailureReason() == null ? "" : "  reason=\"" + c.getFailureReason() + "\""));
        }

        out.println("--- A: irrigation slots ---");
        if (s.slots().isEmpty()) {
            out.println("  (none)");
        }
        for (IrrigationSlot slot : s.slots()) {
            out.println(String.format(Locale.ROOT, "  #%d  bed=%s  liters=%.1f  status=%s",
                    slot.getId(), slot.getBedId(), slot.getLiters(), slot.getStatus()));
        }

        out.println("--- B: irrigation runs ---");
        if (s.runs().isEmpty()) {
            out.println("  (none)");
        }
        for (IrrigationRun run : s.runs()) {
            out.println(String.format(Locale.ROOT, "  #%d  bed=%s  liters=%.1f  status=%s",
                    run.getId(), run.getBedId(), run.getLiters(), run.getStatus()));
        }
        out.println("--- B: ventilation mode: " + s.ventilationMode() + " ---");

        out.println("--- C: dosages ---");
        if (s.dosages().isEmpty()) {
            out.println("  (none)");
        }
        for (DosageRecord d : s.dosages()) {
            out.println(String.format(Locale.ROOT, "  #%d  bed=%s  crop=%s  water=%.1f L  dose=%.1f g  status=%s",
                    d.getId(), d.getBedId(), d.getCrop(), d.getWaterLiters(), d.getGrams(), d.getStatus()));
        }

        out.println("--- C: alerts ---");
        if (s.alerts().isEmpty()) {
            out.println("  (none)");
        }
        for (AlertService.Alert a : s.alerts()) {
            out.println("  #" + a.id() + "  status=" + a.status() + "  \"" + a.message() + "\"");
        }
    }

    private void printCycle(long cycleId) {
        admin.listCycles().stream()
                .filter(c -> c.getId() == cycleId)
                .findFirst()
                .ifPresent(c -> out.println("  " + c));
        out.println("  (option 2 shows what is now on disk)");
    }

    private static String dash(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }

    private CycleRequest readRequest() {
        out.println("(press Enter to accept the value in brackets; try moisture 55 to see a rejected cycle)");
        String bed = ask("Bed id, e.g. B-3", "B-3");
        double moisture = askDouble("Soil moisture, % (0-100)", "20");
        String crop = ask("Crop (tomato / cucumber / pepper / lettuce)", "tomato");
        double temperature = askDouble("Air temperature, C", "26");
        double humidity = askDouble("Air humidity, %", "50");
        return new CycleRequest(bed, moisture, crop, temperature, humidity);
    }

    private String ask(String label, String defaultValue) {
        out.print(defaultValue == null ? label + ": " : label + " [" + defaultValue + "]: ");
        out.flush();
        if (!in.hasNextLine()) {
            throw new InputClosed();
        }
        String line = in.nextLine().trim();
        return line.isEmpty() && defaultValue != null ? defaultValue : line;
    }

    private double askDouble(String label, String defaultValue) {
        String text = ask(label, defaultValue);
        try {
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number");
        }
    }

    private long askLong(String label) {
        String text = ask(label, null);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a whole number");
        }
    }

    private boolean confirm(String question) {
        String answer = ask(question + " (y/n)", "n").toLowerCase(Locale.ROOT);
        return answer.equals("y") || answer.equals("yes");
    }
}
