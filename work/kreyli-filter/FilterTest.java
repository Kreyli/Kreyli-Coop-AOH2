package age.of.civilizations2.jakowski.lukasz.mp;
import java.util.*;

public final class FilterTest {
    private static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    public static void main(String[] args){
        String frame="I\t1\t0\t2\t0\t0\tТестИгрок";
        check(KreyliBrowser.advertise(frame).equals("I\t1\t0\t2\t0\t0\t[KC2]ТестИгрок"),"Listing marker");
        for(String other:new String[]{"H","J ABCDEF","D ABCDEF 12","L","I\tbroken"})check(KreyliBrowser.advertise(other).equals(other),"Non-info frame mutated");
        String[] compatible={"O","ABCDEF","0","2","0","0","[KC2]ТестИгрок"};
        String[] old={"O","OLDOLD","0","1","0","0","[KC1]Old"};
        String[] base={"D","127.0.0.1:1","0","1","0","0","Base"};
        List<String[]> result=KreyliBrowser.filter(Arrays.asList(compatible,old,base,null,new String[0]));
        check(result.size()==1,"Base or incompatible room leaked");
        check(result.get(0)[6].equals("ТестИгрок"),"Displayed nickname changed");
        check(compatible[6].equals("[KC2]ТестИгрок"),"Input row mutated");
        String[] markerNick={"O","ABCDEF","0","1","0","0","[KC2][KC2]Name"};
        check(KreyliBrowser.filter(Collections.singletonList(markerNick)).get(0)[6].equals("[KC2]Name"),"Marker nickname round trip");
        check(KreyliIdentity.cleanNickname("  Иван;\n\tИгрок ").equals("ИванИгрок"),"Nickname normalization");
        check(KreyliIdentity.cleanNickname("123456789012345678901234567").length()==24,"Native nickname bound");
        check(KreyliIdentity.cleanNickname(null).isEmpty(),"Absent nickname");
        System.out.println("KREYLI_FILTER: PASS marker isolation, compatible rows, nickname preservation, normalization");
    }
}
