import java.util.*;

/* =========================== SINGLETON ===================================== */
class LeagueRegistry {
    private static volatile LeagueRegistry INSTANCE;
    private final Map<String, Team> teams = new HashMap<>();
    private final String season;

    private LeagueRegistry(String season) { this.season = season; }

    public static LeagueRegistry getInstance(String season) {
        if (INSTANCE == null) {
            synchronized (LeagueRegistry.class) {
                if (INSTANCE == null) INSTANCE = new LeagueRegistry(season);
            }
        }
        return INSTANCE;
    }

    public void registerTeam(Team team) {
        if (teams.containsKey(team.getName()))
            throw new IllegalArgumentException("Equipo ya registrado: " + team.getName());
        teams.put(team.getName(), team);
    }
    public Collection<Team> listTeams() { return teams.values(); }
    public String getSeason() { return season; }
}

/* ========================= ABSTRACT FACTORY ================================ */
interface FeePolicy { double registrationFee(); }
interface RosterPolicy { int minPlayers(); int maxPlayers(); boolean allowNumber(int number); }
interface MatchPolicy { int matchDurationMinutes(); boolean allowUnlimitedSubs(); }

class DivisionBundle {
    final FeePolicy fee;
    final RosterPolicy roster;
    final MatchPolicy match;
    DivisionBundle(FeePolicy f, RosterPolicy r, MatchPolicy m){ this.fee=f; this.roster=r; this.match=m; }
}

interface DivisionFactory {
    DivisionBundle createPolicies();
    String name();
}

class MaleDivisionFactory implements DivisionFactory {
    public DivisionBundle createPolicies() {
        return new DivisionBundle(
            () -> 50.0,
            new RosterPolicy() {
                public int minPlayers() { return 11; }
                public int maxPlayers() { return 25; }
                public boolean allowNumber(int n){ return n>=1 && n<=99; }
            },
            new MatchPolicy() {
                public int matchDurationMinutes() { return 80; }
                public boolean allowUnlimitedSubs() { return false; }
            }
        );
    }
    public String name() { return "Masculina"; }
}

class FemaleDivisionFactory implements DivisionFactory {
    public DivisionBundle createPolicies() {
        return new DivisionBundle(
            () -> 45.0,
            new RosterPolicy() {
                public int minPlayers() { return 9; }
                public int maxPlayers() { return 22; }
                public boolean allowNumber(int n){ return n>=1 && n<=30; }
            },
            new MatchPolicy() {
                public int matchDurationMinutes() { return 70; }
                public boolean allowUnlimitedSubs() { return true; }
            }
        );
    }
    public String name() { return "Femenina"; }
}

class U17DivisionFactory implements DivisionFactory {
    public DivisionBundle createPolicies() {
        return new DivisionBundle(
            () -> 20.0,
            new RosterPolicy() {
                public int minPlayers() { return 7; }
                public int maxPlayers() { return 20; }
                public boolean allowNumber(int n){ return n>=1 && n<=50; }
            },
            new MatchPolicy() {
                public int matchDurationMinutes() { return 60; }
                public boolean allowUnlimitedSubs() { return true; }
            }
        );
    }
    public String name() { return "Sub-17"; }
}

class SubCuarentaDivisionFactory implements DivisionFactory {
    public DivisionBundle createPolicies() {
        return new DivisionBundle(
            () -> 35.0,
            new RosterPolicy() {
                public int minPlayers() { return 9; }
                public int maxPlayers() { return 22; }
                public boolean allowNumber(int n){ return n>=1 && n<=99; }
            },
            new MatchPolicy() {
                public int matchDurationMinutes() { return 70; }
                public boolean allowUnlimitedSubs() { return true; }
            }
        );
    }
    public String name() { return "SubCuarenta"; }
}

/* ============================== PROTOTYPE ================================== */
class Player implements Cloneable {
    private final String name;
    private final int number;
    public Player(String name, int number) { this.name = name; this.number = number; }
    public String getName(){ return name; }
    public int getNumber(){ return number; }
    @Override public Player clone() { return new Player(name, number); }
    @Override public String toString(){ return number + " - " + name; }
}

/* ============================== TEAM (POJO) ================================ */
class Team {
    private final String name;
    private final String divisionName;
    private final DivisionBundle policies;
    private final String coach;
    private final String captain;
    private final String primaryColor;
    private final List<Player> roster;

    Team(String name, String div, DivisionBundle pol, String coach, String captain, String color, List<Player> roster){
        this.name=name; this.divisionName=div; this.policies=pol; this.coach=coach; this.captain=captain; this.primaryColor=color; this.roster=roster;
    }
    public String getName(){ return name; }
    public List<Player> getRoster(){ return roster; }
    public DivisionBundle getPolicies(){ return policies; }
    @Override public String toString(){
        return "["+divisionName+"] " + name + " ("+primaryColor+") Coach:"+coach+" C:"+captain+" | Jugadores:"+roster.size();
    }
}

/* ============================== BUILDER ==================================== */
class TeamBuilder {
    private String name, coach, captain, color;
    private DivisionFactory factory;
    private DivisionBundle policies;
    private final List<Player> roster = new ArrayList<>();

    public TeamBuilder forDivision(DivisionFactory f){ this.factory=f; this.policies=f.createPolicies(); return this; }
    public TeamBuilder name(String n){ this.name=n; return this; }
    public TeamBuilder coach(String c){ this.coach=c; return this; }
    public TeamBuilder captain(String c){ this.captain=c; return this; }
    public TeamBuilder color(String c){ this.color=c; return this; }

    public TeamBuilder addPlayer(String name, int number){
        if (policies == null) throw new IllegalStateException("Define división antes de agregar jugadores");
        if (!policies.roster.allowNumber(number))
            throw new IllegalArgumentException("Dorsal inválido para esta división: " + number);
        roster.add(new Player(name, number));
        return this;
    }

    public Team build(){
        if (name==null || factory==null || policies==null) throw new IllegalStateException("Faltan datos");
        int size = roster.size();
        if (size < policies.roster.minPlayers() || size > policies.roster.maxPlayers())
            throw new IllegalStateException("Plantilla fuera de límites: " + size);

        Set<Integer> nums = new HashSet<>();
        for (Player p : roster) {
            if (!nums.add(p.getNumber())) throw new IllegalStateException("Dorsal repetido: " + p.getNumber());
        }

        return new Team(name, factory.name(), policies, coach, captain, color, List.copyOf(roster));
    }
}

/* ========================== PROTOTYPE CATALOG ============================== */
class PrototypeCatalog {
    private final Map<String, List<Player>> teamTemplates = new HashMap<>();
    private final Map<String, Player> playerTemplates = new HashMap<>();

    public void putTeamTemplate(String key, List<Player> roster){ teamTemplates.put(key, roster); }
    public List<Player> cloneTeamTemplate(String key){
        List<Player> src = teamTemplates.get(key);
        if (src == null) throw new IllegalArgumentException("No existe plantilla: " + key);
        List<Player> copy = new ArrayList<>();
        for (Player p : src) copy.add(p.clone());
        return copy;
    }

    public void putPlayerTemplate(String key, Player p){ playerTemplates.put(key, p); }
    public Player clonePlayer(String key){
        Player p = playerTemplates.get(key);
        if (p == null) throw new IllegalArgumentException("No existe jugador molde: " + key);
        return p.clone();
    }
}

/* =============================== DEMO ====================================== */
public class mi {
    public static void main(String[] args) {
        // Registro único de liga
        LeagueRegistry registry = LeagueRegistry.getInstance("Temporada-2026A");

        // Divisiones (Abstract Factory)
        DivisionFactory male = new MaleDivisionFactory();
        DivisionFactory female = new FemaleDivisionFactory();
        DivisionFactory subCuarenta = new SubCuarentaDivisionFactory();

        // Catálogo de prototipos (Prototype)
        PrototypeCatalog catalog = new PrototypeCatalog();

        // Plantilla base de 11 jugadores (mínimo para Masculina)
        catalog.putTeamTemplate("MALE-BASE", List.of(
            new Player("J1", 1),  new Player("J2", 2),  new Player("J3", 3),
            new Player("J4", 4),  new Player("J5", 5),  new Player("J6", 6),
            new Player("J7", 7),  new Player("J8", 8),  new Player("J9", 9),
            new Player("J10", 10), new Player("J11", 11)
        ));

        // Plantilla base de 9 jugadores (mínimo para Femenina y SubCuarenta)
        catalog.putTeamTemplate("FEMALE-BASE", List.of(
            new Player("JF1", 1),  new Player("JF2", 2),  new Player("JF3", 3),
            new Player("JF4", 4),  new Player("JF5", 5),  new Player("JF6", 6),
            new Player("JF7", 7),  new Player("JF8", 8),  new Player("JF9", 9)
        ));

        catalog.putTeamTemplate("SUBCUARENTA-BASE", List.of(
            new Player("SC1", 1),  new Player("SC2", 2),  new Player("SC3", 3),
            new Player("SC4", 4),  new Player("SC5", 5),  new Player("SC6", 6),
            new Player("SC7", 7),  new Player("SC8", 8),  new Player("SC9", 9)
        ));

        // Clonar plantillas
        List<Player> baseMale = catalog.cloneTeamTemplate("MALE-BASE");
        List<Player> baseFemale = catalog.cloneTeamTemplate("FEMALE-BASE");
        List<Player> baseSubCuarenta = catalog.cloneTeamTemplate("SUBCUARENTA-BASE");

        // ====== Equipo Masculino ======
        TeamBuilder tbMale = new TeamBuilder()
                .forDivision(male)
                .name("Leones FC")
                .coach("DT Carlos Ruiz")
                .captain("J10")
                .color("Negro");

        for (Player p : baseMale) tbMale.addPlayer(p.getName(), p.getNumber());

        Team leones = tbMale.build();
        registry.registerTeam(leones);

        // ====== Equipo Femenino ======
        TeamBuilder tbFemale = new TeamBuilder()
                .forDivision(female)
                .name("Leonas FC")
                .coach("DT Ana Martínez")
                .captain("JF5")
                .color("Azul");

        for (Player p : baseFemale) tbFemale.addPlayer(p.getName(), p.getNumber());
        // Añadimos jugadoras adicionales
        tbFemale.addPlayer("JF10", 10).addPlayer("JF11", 11);

        Team leonas = tbFemale.build();
        registry.registerTeam(leonas);

        // ====== Equipo SubCuarenta ======
        TeamBuilder tbSubCuarenta = new TeamBuilder()
                .forDivision(subCuarenta)
                .name("Veteranos FC")
                .coach("DT Roberto Gómez")
                .captain("SC4")
                .color("Rojo");

        for (Player p : baseSubCuarenta) tbSubCuarenta.addPlayer(p.getName(), p.getNumber());
        // Añadimos jugadores adicionales
        tbSubCuarenta.addPlayer("SC10", 10).addPlayer("SC11", 11).addPlayer("SC12", 12);

        Team veteranos = tbSubCuarenta.build();
        registry.registerTeam(veteranos);

        // Mostrar resultado
        System.out.println("Liga: " + registry.getSeason());
        registry.listTeams().forEach(t -> {
            System.out.println(t);
            System.out.println("  Fee: $" + t.getPolicies().fee.registrationFee());
            System.out.println("  Duración partido: " + t.getPolicies().match.matchDurationMinutes() + " min");
            System.out.println("  Cambios ilimitados: " + t.getPolicies().match.allowUnlimitedSubs());
            System.out.println("  Plantilla: ");
            t.getRoster().forEach(p -> System.out.println("    " + p));
            System.out.println();
        });
    }
}