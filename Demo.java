import java.util.*;

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
            () -> 30.0,
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

class Player implements Cloneable {
    private final String name;
    private final int number;
    public Player(String name, int number) { this.name = name; this.number = number; }
    public String getName(){ return name; }
    public int getNumber(){ return number; }
    @Override public Player clone() { return new Player(name, number); }
    @Override public String toString(){ return number + " - " + name; }
}

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

public class Demo {
    public static void main(String[] args) {
        LeagueRegistry registry = LeagueRegistry.getInstance("Temporada-2025A");

        DivisionFactory u17 = new U17DivisionFactory();

        PrototypeCatalog catalog = new PrototypeCatalog();
        catalog.putTeamTemplate("U17-BASE", List.of(
            new Player("Jugador A", 1), new Player("Jugador B", 2),
            new Player("Jugador C", 3), new Player("Jugador D", 4),
            new Player("Jugador E", 5), new Player("Jugador F", 6),
            new Player("Jugador G", 7)
        ));
        catalog.putPlayerTemplate("ARQUERO-BASE", new Player("Arquero Base", 1));

        List<Player> base = catalog.cloneTeamTemplate("U17-BASE");

        TeamBuilder tb = new TeamBuilder()
                .forDivision(u17)
                .name("Dragones U17")
                .coach("DT Juan Pérez")
                .captain("Jugador G")
                .color("Rojo");

        for (Player p : base) tb.addPlayer(p.getName(), p.getNumber());

        Player arq = catalog.clonePlayer("ARQUERO-BASE");
        tb.addPlayer("Arquero Luis", 12);

        Team dragones = tb.build();

        registry.registerTeam(dragones);

        System.out.println("Liga: " + registry.getSeason());
        registry.listTeams().forEach(t -> {
            System.out.println(t);
            System.out.println("  Fee: $" + t.getPolicies().fee.registrationFee());
            System.out.println("  Duración partido: " + t.getPolicies().match.matchDurationMinutes() + " min");
            System.out.println("  Plantilla: ");
            t.getRoster().forEach(p -> System.out.println("    " + p));
        });

        DivisionFactory femenina = new FemaleDivisionFactory();
        TeamBuilder tb2 = new TeamBuilder()
                .forDivision(femenina)
                .name("Leonas FC")
                .coach("DT María López")
                .captain("Capitana X")
                .color("Azul");
        tb2.addPlayer("Ana", 10).addPlayer("Bea", 7).addPlayer("Caro", 3)
           .addPlayer("Diana", 8).addPlayer("Eva", 2).addPlayer("Fabi", 12)
           .addPlayer("Gabi", 14).addPlayer("Hanna", 18).addPlayer("Irene", 22);
        try {
            Team leonas = tb2.build(); //
            registry.registerTeam(leonas);
        } catch (Exception e) {
            System.out.println("No se pudo registrar Leonas FC: " + e.getMessage());
        }
    }
}