import java.util.*;

enum Size { SMALL, MEDIUM, LARGE }
enum Crust { THIN, NEAPOLITAN, SICILIAN }

class POSRegistry {
    private static volatile POSRegistry INSTANCE;
    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final String businessDay;
    private POSRegistry(String businessDay){ this.businessDay = businessDay; }
    public static POSRegistry getInstance(String businessDay){
        if(INSTANCE==null){
            synchronized (POSRegistry.class){
                if(INSTANCE==null) INSTANCE = new POSRegistry(businessDay);
            }
        }
        return INSTANCE;
    }
    public void saveOrder(Order order){
        if(orders.containsKey(order.getId()))
            throw new IllegalArgumentException("Orden duplicada: " + order.getId());
        orders.put(order.getId(), order);
    }
    public Collection<Order> listOrders(){ return orders.values(); }
    public String getBusinessDay(){ return businessDay; }
}

interface PricePolicy {
    double basePrice(Size size);
    double crustSurcharge(Crust crust);
    double toppingPrice(String topping);
}
interface TaxPolicy { double taxRate(); }

class OvenPolicy {
    final int bakeMinutes;
    final int tempC;
    OvenPolicy(int m, int t){ bakeMinutes=m; tempC=t; }
}

class StyleBundle {
    final PricePolicy price;
    final TaxPolicy tax;
    final OvenPolicy oven;
    StyleBundle(PricePolicy p, TaxPolicy t, OvenPolicy o){ price=p; tax=t; oven=o; }
}

interface StyleFactory {
    StyleBundle createPolicies();
    String name();
}

class NYStyleFactory implements StyleFactory {
    public StyleBundle createPolicies(){
        return new StyleBundle(
            new PricePolicy(){
                public double basePrice(Size s){
                    return switch(s){ case SMALL->8; case MEDIUM->10; case LARGE->12; };
                }
                public double crustSurcharge(Crust c){
                    return switch(c){
                        case THIN -> 0;
                        case NEAPOLITAN -> 1;
                        case SICILIAN -> 2.5;
                    };
                }
                public double toppingPrice(String t){ return 1.5; }
            },
            () -> 0.08,
            new OvenPolicy(12, 315)
        );
    }
    public String name(){ return "New York"; }
}

class NeapolitanFactory implements StyleFactory {
    public StyleBundle createPolicies(){
        return new StyleBundle(
            new PricePolicy(){
                public double basePrice(Size s){
                    return switch(s){ case SMALL->9; case MEDIUM->12; case LARGE->14; };
                }
                public double crustSurcharge(Crust c){
                    return switch(c){
                        case NEAPOLITAN -> 0;
                        case THIN -> 0.5;
                        case SICILIAN -> 3.0;
                    };
                }
                public double toppingPrice(String t){
                    Set<String> premium = Set.of("Bufala","Prosciutto","Porcini");
                    return premium.contains(t) ? 2.5 : 2.0;
                }
            },
            () -> 0.10,
            new OvenPolicy(90/2, 430) // horno muy caliente, tiempo corto (~45s)
        );
    }
    public String name(){ return "Neapolitan"; }
}

class Pizza {
    private final String styleName;
    private final StyleBundle policies;
    private final Size size;
    private final Crust crust;
    private final List<String> toppings;
    Pizza(String styleName, StyleBundle pol, Size size, Crust crust, List<String> toppings){
        this.styleName=styleName; this.policies=pol; this.size=size; this.crust=crust; this.toppings=toppings;
    }
    public double price(){
        double subtotal = policies.price.basePrice(size) + policies.price.crustSurcharge(crust);
        for(String t: toppings) subtotal += policies.price.toppingPrice(t);
        return subtotal;
    }
    public String summary(){
        return styleName + " " + size + " " + crust + " " + toppings;
    }
    public int bakeMinutes(){ return policies.oven.bakeMinutes; }
    public int bakeTempC(){ return policies.oven.tempC; }
}

class PizzaBuilder {
    private StyleFactory styleFactory;
    private StyleBundle policies;
    private Size size = Size.MEDIUM;
    private Crust crust = Crust.THIN;
    private final List<String> toppings = new ArrayList<>();
    public PizzaBuilder forStyle(StyleFactory f){ this.styleFactory=f; this.policies=f.createPolicies(); return this; }
    public PizzaBuilder size(Size s){ this.size=s; return this; }
    public PizzaBuilder crust(Crust c){ this.crust=c; return this; }
    public PizzaBuilder addTopping(String t){ toppings.add(t); return this; }
    public Pizza build(){
        if(styleFactory==null || policies==null) throw new IllegalStateException("Define estilo antes de construir");
        if(toppings.size()>12) throw new IllegalStateException("Demasiados toppings");
        return new Pizza(styleFactory.name(), policies, size, crust, List.copyOf(toppings));
    }
}

class PizzaRecipe implements Cloneable {
    private Size size;
    private Crust crust;
    private final List<String> toppings = new ArrayList<>();
    public PizzaRecipe(Size s, Crust c, List<String> t){ size=s; crust=c; toppings.addAll(t); }
    public Size getSize(){ return size; }
    public Crust getCrust(){ return crust; }
    public List<String> getToppings(){ return toppings; }
    @Override public PizzaRecipe clone(){
        return new PizzaRecipe(size, crust, new ArrayList<>(toppings));
    }
}

class RecipeCatalog {
    private final Map<String, PizzaRecipe> recipes = new HashMap<>();
    public void put(String key, PizzaRecipe r){ recipes.put(key, r); }
    public PizzaRecipe cloneRecipe(String key){
        PizzaRecipe r = recipes.get(key);
        if(r==null) throw new IllegalArgumentException("No existe receta: " + key);
        return r.clone();
    }
}

class Order {
    private final String id;
    private final String customer;
    private final List<Pizza> pizzas;
    private final double taxRate; // mezcla estilos: aplicamos promedio ponderado simple por precio antes de impuesto
    private final double subtotal;
    private final double tax;
    private final double total;
    Order(String id, String customer, List<Pizza> pizzas){
        this.id=id; this.customer=customer; this.pizzas=List.copyOf(pizzas);
        double sum = 0.0;
        double weightedTax = 0.0;
        for(Pizza p: pizzas){
            double psub = p.price();
            sum += psub;
            // aproximación: usar 8% si NY, 10% si Neapolitan, detectado por summary
            double rate = p.summary().startsWith("New York") ? 0.08 : 0.10;
            weightedTax += psub * rate;
        }
        this.subtotal = sum;
        this.tax = weightedTax;
        this.total = subtotal + tax;
        this.taxRate = pizzas.isEmpty()? 0.0 : tax/subtotal;
    }
    public String getId(){ return id; }
    public String receipt(){
        StringBuilder sb = new StringBuilder();
        sb.append("Orden ").append(id).append(" - Cliente: ").append(customer).append("\n");
        int i=1;
        for(Pizza p: pizzas){
            sb.append("  ").append(i++).append(") ").append(p.summary())
              .append(" | Subtotal: $").append(String.format(Locale.US,"%.2f", p.price()))
              .append(" | Horno: ").append(p.bakeTempC()).append("°C ").append(p.bakeMinutes()).append("min\n");
        }
        sb.append("Subtotal: $").append(String.format(Locale.US,"%.2f", subtotal)).append("\n");
        sb.append("Impuestos (~").append(String.format(Locale.US,"%.1f", taxRate*100)).append("%): $")
          .append(String.format(Locale.US,"%.2f", tax)).append("\n");
        sb.append("Total: $").append(String.format(Locale.US,"%.2f", total)).append("\n");
        return sb.toString();
    }
}

public class PizzaDemo {
    public static void main(String[] args) {
        POSRegistry pos = POSRegistry.getInstance("2025-10-20");

        StyleFactory ny = new NYStyleFactory();
        StyleFactory nap = new NeapolitanFactory();

        RecipeCatalog catalog = new RecipeCatalog();
        catalog.put("MARGHERITA", new PizzaRecipe(
                Size.MEDIUM, Crust.NEAPOLITAN, List.of("Tomato","Mozzarella","Basil")));
        catalog.put("PEPPERONI", new PizzaRecipe(
                Size.LARGE, Crust.THIN, List.of("Tomato","Mozzarella","Pepperoni")));

        PizzaRecipe rec1 = catalog.cloneRecipe("MARGHERITA");
        Pizza margheritaNap = new PizzaBuilder()
                .forStyle(nap)
                .size(rec1.getSize())
                .crust(rec1.getCrust())
                .addTopping(rec1.getToppings().get(0))
                .addTopping(rec1.getToppings().get(1))
                .addTopping(rec1.getToppings().get(2))
                .build();

        PizzaRecipe rec2 = catalog.cloneRecipe("PEPPERONI");
        Pizza pepperoniNY = new PizzaBuilder()
                .forStyle(ny)
                .size(rec2.getSize())
                .crust(rec2.getCrust())
                .addTopping(rec2.getToppings().get(0))
                .addTopping(rec2.getToppings().get(1))
                .addTopping(rec2.getToppings().get(2))
                .addTopping("Olives")
                .build();

        Pizza customNY = new PizzaBuilder()
                .forStyle(ny)
                .size(Size.SMALL)
                .crust(Crust.SICILIAN)
                .addTopping("Mushrooms")
                .addTopping("Onions")
                .addTopping("Green Peppers")
                .build();

        Order order1 = new Order(UUID.randomUUID().toString(), "Roberto", List.of(margheritaNap, pepperoniNY, customNY));
        pos.saveOrder(order1);

        System.out.println("Día de operación: " + pos.getBusinessDay());
        for(Order o: pos.listOrders()){
            System.out.println(o.receipt());
        }

        Pizza risky = null;
        try{
            risky = new PizzaBuilder().forStyle(nap)
                    .size(Size.LARGE)
                    .crust(Crust.SICILIAN)
                    .build();
            Order order2 = new Order(UUID.randomUUID().toString(), "Cliente Walk-in", List.of(risky));
            pos.saveOrder(order2);
            System.out.println(order2.receipt());
        }catch(Exception e){
            System.out.println("No se pudo crear/guardar la pizza u orden: " + e.getMessage());
        }
    }
}