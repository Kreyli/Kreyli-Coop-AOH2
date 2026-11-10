package age.of.civilizations2.jakowski.lukasz.mp;

import java.util.Comparator;
import java.util.List;

/** Host-side policy for a one-hour, turn-based Quick Match. */
public final class KreyliQuickRules {
    public static final long MATCH_MILLIS=3_600_000, TURN_MILLIS=20_000;
    public static final int CONQUEST_COOLDOWN_TURNS=65;
    public enum Scenario { WORLD_WAR_II, MODERN_WORLD }
    public enum Continent { AFRICA, NORTH_AMERICA, SOUTH_AMERICA, EUROPE, ASIA, AUSTRALIA }
    public record Standing(String player,int rank,boolean alive,int eliminatedTurn,int provinces,int playersDefeated) {}

    private KreyliQuickRules() {}

    public static boolean canConquerPlayer(int turn,int lastConquestTurn){
        return lastConquestTurn<0||(long)turn-lastConquestTurn>=CONQUEST_COOLDOWN_TURNS;
    }

    public static boolean shouldFinish(List<Standing> standings,long elapsed){
        return elapsed>=MATCH_MILLIS||standings.stream().filter(Standing::alive).limit(2).count()<2;
    }

    public static List<Standing> results(List<Standing> standings){
        return standings.stream().sorted(Comparator.comparing(Standing::alive).reversed()
                .thenComparing(Comparator.comparingInt(Standing::eliminatedTurn).reversed())
                .thenComparingInt(Standing::rank)
                .thenComparing(Standing::player)).toList();
    }
}
