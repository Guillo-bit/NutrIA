import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

enum Specialty { CLINICAL, CHILD, ORGANIZATIONAL }
enum Modality { IN_PERSON, ONLINE, GROUP }
enum SessionType { INDIVIDUAL, FAMILY, GROUP }

class Patient {
    private final String id;
    private final String name;
    public Patient(String id, String name){ this.id=id; this.name=name; }
    public String getId(){ return id; }
    public String getName(){ return name; }
    @Override public String toString(){ return name + " ("+id+")"; }
}

class Psychologist {
    private final String id;
    private final String name;
    private final Specialty specialty;
    public Psychologist(String id, String name, Specialty specialty){
        this.id=id; this.name=name; this.specialty=specialty;
    }
    public String getId(){ return id; }
    public String getName(){ return name; }
    public Specialty getSpecialty(){ return specialty; }
    @Override public String toString(){ return name + " ["+specialty+"]"; }
}

interface FeePolicy { double fee(SessionType type, Modality modality); }
interface DurationPolicy { int minutes(SessionType type); }
interface MaterialsPolicy { List<String> required(Modality modality); }

class SpecialtyBundle {
    final FeePolicy fee;
    final DurationPolicy duration;
    final MaterialsPolicy materials;
    SpecialtyBundle(FeePolicy f, DurationPolicy d, MaterialsPolicy m){ this.fee=f; this.duration=d; this.materials=m; }
}

interface SpecialtyFactory {
    SpecialtyBundle createPolicies();
    String name();
}

class ClinicalFactory implements SpecialtyFactory {
    public SpecialtyBundle createPolicies(){
        return new SpecialtyBundle(
            (type, modality) -> switch (type) {
                case INDIVIDUAL -> 45.0;
                case FAMILY -> 60.0;
                case GROUP -> 35.0;
            },
            type -> switch (type) {
                case INDIVIDUAL -> 50;
                case FAMILY -> 60;
                case GROUP -> 45;
            },
            modality -> switch (modality) {
                case IN_PERSON -> List.of("Room-201","Couch","ConsentForm");
                case ONLINE    -> List.of("VideoLink","ConsentForm");
                case GROUP     -> List.of("Room-305","CircleChairs","ConsentForm");
            }
        );
    }
    public String name(){ return "Clinical"; }
}

class ChildFactory implements SpecialtyFactory {
    public SpecialtyBundle createPolicies(){
        return new SpecialtyBundle(
            (type, modality) -> switch (type) {
                case INDIVIDUAL -> 40.0;
                case FAMILY -> 55.0;
                case GROUP -> 30.0;
            },
            type -> switch (type) {
                case INDIVIDUAL -> 45;
                case FAMILY -> 55;
                case GROUP -> 40;
            },
            modality -> switch (modality) {
                case IN_PERSON -> List.of("Room-Kids","Toys","ParentConsent");
                case ONLINE    -> List.of("VideoLink","ParentConsent");
                case GROUP     -> List.of("Room-Kids-2","ArtMaterials","ParentConsent");
            }
        );
    }
    public String name(){ return "Child"; }
}

class OrganizationalFactory implements SpecialtyFactory {
    public SpecialtyBundle createPolicies(){
        return new SpecialtyBundle(
            (type, modality) -> switch (type) {
                case INDIVIDUAL -> 65.0;
                case FAMILY -> 70.0;
                case GROUP -> 50.0;
            },
            type -> switch (type) {
                case INDIVIDUAL -> 60;
                case FAMILY -> 60;
                case GROUP -> 50;
            },
            modality -> switch (modality) {
                case IN_PERSON -> List.of("MeetingRoom","Questionnaires");
                case ONLINE    -> List.of("VideoLink","Questionnaires");
                case GROUP     -> List.of("Auditorium","Projector","Questionnaires");
            }
        );
    }
    public String name(){ return "Organizational"; }
}

class Appointment {
    private final String id;
    private final Patient patient;
    private final Psychologist psychologist;
    private final String specialtyName;
    private final SpecialtyBundle policies;
    private final LocalDateTime start;
    private final LocalDateTime end;
    private final Modality modality;
    private final SessionType sessionType;
    private final double fee;
    private final List<String> materials;
    private final String notes;

    Appointment(String id, Patient patient, Psychologist psych, String specName, SpecialtyBundle pol,
                LocalDateTime start, LocalDateTime end, Modality modality, SessionType type,
                double fee, List<String> materials, String notes){
        this.id=id; this.patient=patient; this.psychologist=psych; this.specialtyName=specName; this.policies=pol;
        this.start=start; this.end=end; this.modality=modality; this.sessionType=type;
        this.fee=fee; this.materials=materials; this.notes=notes;
    }
    public String getId(){ return id; }
    public Patient getPatient(){ return patient; }
    public Psychologist getPsychologist(){ return psychologist; }
    public LocalDateTime getStart(){ return start; }
    public LocalDateTime getEnd(){ return end; }
    public double getFee(){ return fee; }
    public String summary(){
        return "["+specialtyName+"] " + sessionType + " " + modality + " | " +
               psychologist + " ↔️ " + patient + " | " +
               start + " - " + end + " | $" + String.format(Locale.US,"%.2f", fee);
    }
    public List<String> getMaterials(){ return materials; }
    public String getNotes(){ return notes; }
}

class AppointmentBuilder {
    private SpecialtyFactory factory;
    private SpecialtyBundle policies;

    private Patient patient;
    private Psychologist psychologist;
    private LocalDateTime start;
    private Modality modality = Modality.IN_PERSON;
    private SessionType sessionType = SessionType.INDIVIDUAL;
    private String notes = "";

    public AppointmentBuilder forSpecialty(SpecialtyFactory f){ this.factory=f; this.policies=f.createPolicies(); return this; }
    public AppointmentBuilder patient(Patient p){ this.patient=p; return this; }
    public AppointmentBuilder psychologist(Psychologist p){ this.psychologist=p; return this; }
    public AppointmentBuilder startAt(LocalDateTime dt){ this.start=dt; return this; }
    public AppointmentBuilder modality(Modality m){ this.modality=m; return this; }
    public AppointmentBuilder sessionType(SessionType t){ this.sessionType=t; return this; }
    public AppointmentBuilder notes(String n){ this.notes=n; return this; }

    public Appointment build(){
        if(factory==null || policies==null) throw new IllegalStateException("Falta especialidad");
        if(patient==null) throw new IllegalStateException("Falta paciente");
        if(psychologist==null) throw new IllegalStateException("Falta psicólogo");
        if(start==null) throw new IllegalStateException("Falta fecha/hora de inicio");

        int minutes = policies.duration.minutes(sessionType);
        LocalDateTime end = start.plusMinutes(minutes);
        double fee = policies.fee.fee(sessionType, modality);
        List<String> mats = List.copyOf(policies.materials.required(modality));

        String id = UUID.randomUUID().toString();
        return new Appointment(id, patient, psychologist, factory.name(), policies, start, end, modality, sessionType, fee, mats, notes);
    }
}

class AppointmentRegistry {
    private static volatile AppointmentRegistry INSTANCE;
    private final String businessDay;
    private final Map<String, Appointment> appointments = new LinkedHashMap<>();

    private AppointmentRegistry(String businessDay){ this.businessDay=businessDay; }
    public static AppointmentRegistry getInstance(String businessDay){
        if(INSTANCE==null){
            synchronized (AppointmentRegistry.class){
                if(INSTANCE==null) INSTANCE = new AppointmentRegistry(businessDay);
            }
        }
        return INSTANCE;
    }
    public void save(Appointment a){
        for(Appointment other : appointments.values()){
            boolean samePsych = other.getPsychologist().getId().equals(a.getPsychologist().getId());
            boolean overlap = !a.getEnd().isBefore(other.getStart()) && !a.getStart().isAfter(other.getEnd());
            if(samePsych && overlap){
                throw new IllegalStateException("Conflicto: " + a.getPsychologist().getName() +
                        " ya tiene una cita entre " + other.getStart() + " y " + other.getEnd());
            }
        }
        appointments.put(a.getId(), a);
    }
    public Collection<Appointment> list(){ return appointments.values(); }
    public String getBusinessDay(){ return businessDay; }
    public List<Appointment> byPsychologist(String psychId){
        return appointments.values().stream().filter(a->a.getPsychologist().getId().equals(psychId)).collect(Collectors.toList());
    }
}

public class PsychAppointmentsCleanDemo {
    public static void main(String[] args) {
        AppointmentRegistry reg = AppointmentRegistry.getInstance(LocalDate.now().toString());

        Patient p1 = new Patient("P-001", "Ana Torres");
        Patient p2 = new Patient("P-002", "Luis Pérez");
        Psychologist s1 = new Psychologist("T-CL-01","Dra. Rivera", Specialty.CLINICAL);
        Psychologist s2 = new Psychologist("T-CH-02","Lic. Gómez", Specialty.CHILD);

        SpecialtyFactory clinical = new ClinicalFactory();
        SpecialtyFactory child = new ChildFactory();
        SpecialtyFactory org = new OrganizationalFactory();

        Appointment a1 = new AppointmentBuilder()
                .forSpecialty(clinical)
                .patient(p1)
                .psychologist(s1)
                .sessionType(SessionType.INDIVIDUAL)
                .modality(Modality.IN_PERSON)
                .notes("Primera sesión clínica")
                .startAt(LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0)))
                .build();
        reg.save(a1);

        Appointment a2 = new AppointmentBuilder()
                .forSpecialty(child)
                .patient(p2)
                .psychologist(s2)
                .sessionType(SessionType.GROUP)
                .modality(Modality.GROUP)
                .notes("Juego terapéutico grupal")
                .startAt(LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0)))
                .build();
        reg.save(a2);

        Appointment a3 = new AppointmentBuilder()
                .forSpecialty(org)
                .patient(new Patient("P-003","María Cedeño"))
                .psychologist(new Psychologist("T-OR-03","MSc. Herrera", Specialty.ORGANIZATIONAL))
                .sessionType(SessionType.INDIVIDUAL)
                .modality(Modality.ONLINE)
                .notes("Coaching ejecutivo – seguimiento")
                .startAt(LocalDateTime.of(LocalDate.now(), LocalTime.of(11, 0)))
                .build();
        reg.save(a3);

        System.out.println("Agenda del día: " + reg.getBusinessDay());
        for(Appointment ap : reg.list()){
            System.out.println(" - " + ap.summary());
            System.out.println("   Materiales: " + ap.getMaterials());
            System.out.println("   Notas: " + ap.getNotes());
        }

        try {
            Appointment conflict = new AppointmentBuilder()
                    .forSpecialty(clinical)
                    .patient(new Patient("P-004","Carlos Lara"))
                    .psychologist(s1)
                    .sessionType(SessionType.FAMILY)
                    .modality(Modality.IN_PERSON)
                    .startAt(LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 30)))
                    .build();
            reg.save(conflict);
        } catch (Exception e) {
            System.out.println("No se pudo agendar cita: " + e.getMessage());
        }
    }
}