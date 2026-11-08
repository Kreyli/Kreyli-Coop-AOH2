package age.of.civilizations2.jakowski.lukasz.mp;
/** Readiness must describe the exact turn and the last command that preceded it. */
final class KreyliReadyVote {
    static boolean valid(int worldTurn,int packetTurn,int processedCommands,int requestedCommands,long lastSequence,long sequence){
        return packetTurn==worldTurn&&requestedCommands>=processedCommands&&requestedCommands>=0&&sequence>lastSequence;
    }
}
