

import static org.junit.jupiter.api.Assertions.*;

import greenhouse.a.IrrigationPolicy;
import greenhouse.a.IrrigationScheduler;
import greenhouse.a.IrrigationSlot;
import greenhouse.a.SlotRepository;
import greenhouse.a.SoilSensorArray;
import greenhouse.b.IrrigationController;
import greenhouse.b.IrrigationRun;
import greenhouse.b.RunRepository;
import greenhouse.b.VentilationPolicy;
import greenhouse.b.VentilationPolicy.Mode;
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
import greenhouse.facade.GreenhouseFacade.CycleRequest;
import greenhouse.facade.exceptions.GreenhouseException;
import greenhouse.facade.exceptions.IrrigationFailedException;
import greenhouse.facade.exceptions.NutrientFailedException;
import java.nio.file.Path;
import java.util.List;

public class GreenhouseDsl {

    private final String dbPath;

    public SlotRepository slots;
    public SoilSensorArray sensors;
    public IrrigationScheduler scheduler;
    public RunRepository runs;
    public IrrigationController irrigation;
    public VentilationUnit ventilation;
    public DosageRepository dosages;
    public NutrientDispenser dispenser;
    public AlertService alerts;
    public CycleRepository cycles;
    public GreenhouseFacade facade;
    public GreenhouseAdminFacade admin;

    private String bed = "B-3";
    private double moisture = 20;
    private String crop = "tomato";
    private double temperature = 26;
    private double humidity = 50;

    private CycleRecord cycle;
    private GreenhouseException failure;
    private List<CycleRecord> recovered = List.of();

    private GreenhouseDsl(Path dir) {
        this.dbPath = dir.resolve("greenhouse.db").toString();
        wire();
    }

    public static GreenhouseDsl onFreshDatabase(Path dir) {
        return new GreenhouseDsl(dir);
    }

    private void wire() {
        slots = new SlotRepository(dbPath);
        sensors = new SoilSensorArray(slots);
        scheduler = new IrrigationScheduler(slots, sensors, new IrrigationPolicy());
        runs = new RunRepository(dbPath);
        irrigation = new IrrigationController(runs);
        ventilation = new VentilationUnit(runs, new VentilationPolicy());
        dosages = new DosageRepository(dbPath);
        dispenser = new NutrientDispenser(dosages, new DosageCalculator());
        alerts = new AlertService(dosages);
        cycles = new CycleRepository(dbPath);
        facade = new GreenhouseFacade(sensors, scheduler, irrigation, ventilation, dispenser, alerts, cycles);
        admin = new GreenhouseAdminFacade(facade, cycles, slots, runs, dosages, irrigation, dispenser);
    }

    public GreenhouseDsl givenDryBed(String bedId) {
        this.bed = bedId;
        this.moisture = 20;
        return this;
    }

    public GreenhouseDsl givenWetBed(String bedId) {
        this.bed = bedId;
        this.moisture = 55;
        return this;
    }

    public GreenhouseDsl givenCrop(String cropName) {
        this.crop = cropName;
        return this;
    }

    public GreenhouseDsl givenAir(double temperatureC, double humidityPercent) {
        this.temperature = temperatureC;
        this.humidity = humidityPercent;
        return this;
    }

    public GreenhouseDsl givenCycleInterruptedAfterStepB() {
        buildInterruptedCycle(false);
        return this;
    }

    public GreenhouseDsl givenCycleInterruptedAfterDosage() {
        buildInterruptedCycle(true);
        return this;
    }

    private void buildInterruptedCycle(boolean withDosage) {
        String canonicalCrop = DosageCalculator.Crop.fromName(crop).name();
        sensors.recordMoisture(bed, moisture);
        IrrigationSlot slot = scheduler.reserve(bed);
        CycleRecord record = CycleRecord.begin(bed, canonicalCrop, temperature, humidity, slot.getId());
        IrrigationRun run = irrigation.start(bed, slot.getLiters());
        record.attachRun(run.getId());
        ventilation.engage(temperature, humidity);
        record.advanceToPendingC();
        if (withDosage) {
            DosageRecord dosage = dispenser.dispense(bed, slot.getLiters(), canonicalCrop);
            record.attachDosage(dosage.getId());
            record.attachAlert(alerts.create("Fertilizer dispensed for " + bed).id());
        }
        cycles.save(record);
    }

    public GreenhouseDsl whenCycleRuns() {
        runCycle();
        return this;
    }

    public GreenhouseDsl whenIrrigationPumpFails() {
        admin.simulatePumpFailure(true);
        runCycle();
        return this;
    }

    public GreenhouseDsl whenDispenserFails() {
        admin.simulateDispenserFailure(true);
        runCycle();
        return this;
    }

    public GreenhouseDsl whenApplicationRestarts() {
        wire();
        return this;
    }

    public GreenhouseDsl whenFacadeRecovers() {
        recovered = facade.recoverInterrupted();
        return this;
    }

    private void runCycle() {
        cycle = null;
        failure = null;
        try {
            cycle = facade.runCycle(new CycleRequest(bed, moisture, crop, temperature, humidity));
        } catch (GreenhouseException e) {
            failure = e;
        }
    }

    public GreenhouseDsl thenCycleCompleted() {
        assertNull(failure, () -> "Unexpected failure: " + failure);
        assertNotNull(cycle);
        assertEquals(CycleRecord.Status.COMPLETED, cycle.getStatus());
        assertEquals(CycleRecord.Status.COMPLETED, cycles.findById(cycle.getId()).orElseThrow().getStatus());
        return this;
    }

    public GreenhouseDsl thenFailureIs(Class<? extends GreenhouseException> type) {
        assertNotNull(failure, "Expected the cycle to fail with " + type.getSimpleName());
        assertInstanceOf(type, failure);
        return this;
    }

    public GreenhouseDsl thenFailureCarriesTheCompensatedCycleId() {
        long expected = onlyCycle().getId();
        if (failure instanceof IrrigationFailedException e) {
            assertEquals(expected, e.getCycleId());
        } else if (failure instanceof NutrientFailedException e) {
            assertEquals(expected, e.getCycleId());
        } else {
            fail("Failure does not carry a cycle id: " + failure);
        }
        return this;
    }

    public GreenhouseDsl thenCycleWasCompensated() {
        return thenOnlyCycleIs(CycleRecord.Status.COMPENSATED);
    }

    public GreenhouseDsl thenOnlyCycleIs(CycleRecord.Status status) {
        assertEquals(status, onlyCycle().getStatus());
        return this;
    }

    public GreenhouseDsl thenCycleCountIs(int count) {
        assertEquals(count, cycles.findAll().size());
        return this;
    }

    public GreenhouseDsl thenRecoveredCycleCountIs(int count) {
        assertEquals(count, recovered.size());
        return this;
    }

    public GreenhouseDsl thenNothingWasPersisted() {
        assertTrue(cycles.findAll().isEmpty(), "cycles");
        assertTrue(slots.findAll().isEmpty(), "slots");
        assertTrue(runs.findAll().isEmpty(), "runs");
        assertTrue(dosages.findAll().isEmpty(), "dosages");
        assertTrue(dosages.findAllAlerts().isEmpty(), "alerts");
        return this;
    }

    public GreenhouseDsl thenSlotIs(IrrigationSlot.Status status) {
        assertEquals(1, slots.findAll().size(), "expected exactly one slot");
        assertEquals(status, slots.findAll().get(0).getStatus());
        return this;
    }

    public GreenhouseDsl thenSlotIsReleased() {
        return thenSlotIs(IrrigationSlot.Status.RELEASED);
    }

    public GreenhouseDsl thenSlotIsConfirmed() {
        return thenSlotIs(IrrigationSlot.Status.CONFIRMED);
    }

    public GreenhouseDsl thenNoIrrigationRunExists() {
        assertTrue(runs.findAll().isEmpty());
        return this;
    }

    public GreenhouseDsl thenIrrigationRunIs(IrrigationRun.Status status) {
        assertEquals(1, runs.findAll().size(), "expected exactly one run");
        assertEquals(status, runs.findAll().get(0).getStatus());
        return this;
    }

    public GreenhouseDsl thenVentilationIs(Mode mode) {
        assertEquals(mode, ventilation.currentMode());
        return this;
    }

    public GreenhouseDsl thenNoDosageExists() {
        assertTrue(dosages.findAll().isEmpty());
        return this;
    }

    public GreenhouseDsl thenDosageIs(DosageRecord.Status status) {
        assertEquals(1, dosages.findAll().size(), "expected exactly one dosage");
        assertEquals(status, dosages.findAll().get(0).getStatus());
        return this;
    }

    public GreenhouseDsl thenAlertIs(AlertService.Status status) {
        assertEquals(1, dosages.findAllAlerts().size(), "expected exactly one alert");
        assertEquals(status, dosages.findAllAlerts().get(0).status());
        return this;
    }

    private CycleRecord onlyCycle() {
        assertEquals(1, cycles.findAll().size(), "expected exactly one cycle");
        return cycles.findAll().get(0);
    }
}