package coop;

import java.util.*;

/** Bounded text sidecar; validates every row before native state is touched. */
public final class ArmyStateCodec {
    public record Row(String kind, int civilization, int province, int destination, int amount) {}
    private ArmyStateCodec() {}
    public static String header(int turn, int civs, int provinces) {
        return "coop-army-v1:" + turn + ":" + civs + ":" + provinces + "\n";
    }
    public static List<Row> parse(String text, int turn, int civs, int provinces) {
        if (text == null || text.length() > 16_000_000 || civs < 1 || provinces < 1)
            throw new IllegalArgumentException("Invalid army snapshot bounds");
        List<String> lines = text.lines().toList();
        if (lines.isEmpty() || !lines.get(0).equals(header(turn, civs, provinces).trim()) || lines.size() > 200_001)
            throw new IllegalArgumentException("Army snapshot identity mismatch");
        List<Row> rows = new ArrayList<>(); Set<String> armies = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i).split(":", -1);
            if (p.length != 4 && p.length != 5) throw new IllegalArgumentException("Invalid army row");
            String kind = p[0]; int c, from, to = -1, amount;
            if (kind.equals("army") && p.length == 4) {
                from = Integer.parseInt(p[1]); c = Integer.parseInt(p[2]); amount = Integer.parseInt(p[3]);
                if (!armies.add(from + ":" + c)) throw new IllegalArgumentException("Duplicate army");
            } else if (kind.equals("recruit") && p.length == 4) {
                c = Integer.parseInt(p[1]); from = Integer.parseInt(p[2]); amount = Integer.parseInt(p[3]);
            } else if (kind.equals("move") && p.length == 5) {
                c = Integer.parseInt(p[1]); from = Integer.parseInt(p[2]); to = Integer.parseInt(p[3]); amount = Integer.parseInt(p[4]);
                if (to < 0 || to >= provinces || from == to) throw new IllegalArgumentException("Invalid move destination");
            } else throw new IllegalArgumentException("Unknown army row");
            if (c < 0 || c >= civs || (!kind.equals("army") && c == 0) || from < 0 || from >= provinces || amount <= 0)
                throw new IllegalArgumentException("Invalid army row bounds");
            rows.add(new Row(kind, c, from, to, amount));
        }
        return List.copyOf(rows);
    }
}
