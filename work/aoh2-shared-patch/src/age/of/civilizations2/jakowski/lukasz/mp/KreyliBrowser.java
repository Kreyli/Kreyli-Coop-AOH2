package age.of.civilizations2.jakowski.lukasz.mp;

import java.util.ArrayList;
import java.util.List;

/** Relay discovery namespace only; the separate game handshake enforces compatibility. */
public final class KreyliBrowser {
    public static final String TAG="[KC2]";
    private KreyliBrowser(){}

    /** Only change the display-name field on relay information frames. */
    public static String advertise(String line){
        if(line==null||!line.startsWith("I\t"))return line;
        String[] fields=line.split("\t",-1);
        if(fields.length!=7)return line;
        // Always add our marker: a nickname beginning with the marker must round-trip intact.
        fields[6]=TAG+fields[6];
        return String.join("\t",fields);
    }

    /** Runs before native region labels are appended; no untagged base-mod rooms reach the UI. */
    public static List<String[]> filter(List<String[]> rows){
        ArrayList<String[]> result=new ArrayList<>();
        if(rows==null)return result;
        for(String[] row:rows){
            if(row==null||row.length!=7||row[6]==null||!row[6].startsWith(TAG))continue;
            if(!"O".equals(row[0])&&!"D".equals(row[0]))continue;
            String[] copy=row.clone();copy[6]=copy[6].substring(TAG.length());
            result.add(copy);
        }
        return result;
    }
}
