package age.of.civilizations2.jakowski.lukasz.mp;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;

/** Tests actual transformed native relay I/O without publishing a room. */
public final class NativeFilterProbe {
    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        MP_Net.writeLine(wire,"I\t1\t0\t1\t0\t0\tNativeUser");
        if(!wire.toString(StandardCharsets.UTF_8).equals("I\t1\t0\t1\t0\t0\t[KC2]NativeUser\n"))throw new AssertionError("Missing writeLine hook");
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            FutureTask<Void> relay=new FutureTask<>(()->{
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(5000);
                    if(!"L".equals(new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8)).readLine()))throw new AssertionError("Native list request");
                    socket.getOutputStream().write(("S\tO\tABCDEF\t0\t1\t0\t0\t[KC2]NativeUser\nS\tO\tBASEXX\t0\t1\t0\t0\tBaseUser\n.\n").getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            Thread thread=new Thread(relay,"test-relay");thread.setDaemon(true);thread.start();
            var rows=MP_Net.listServers("127.0.0.1",server.getLocalPort());relay.get(10,TimeUnit.SECONDS);
            if(rows.size()!=1||!rows.get(0)[6].equals("NativeUser"))throw new AssertionError("Missing listServers hook");
        }
        Path fixture=Path.of(args[0]).toAbsolutePath();java.nio.file.Files.createDirectories(fixture);
        Path settings=fixture.resolve("multiplayer.properties");java.nio.file.Files.writeString(settings,"nick=StoredName\n");
        Gdx.files=(Files)java.lang.reflect.Proxy.newProxyInstance(Files.class.getClassLoader(),new Class[]{Files.class},(p,m,a)->{
            if(m.getName().equals("local"))return new FileHandle(settings.toFile());
            throw new UnsupportedOperationException(m.getName());
        });
        System.setProperty("kreyli.nick","LauncherName");MP.loadSettings();
        if(!"LauncherName".equals(MP.nick))throw new AssertionError("Stored settings replaced launcher nick");
        java.nio.file.Files.delete(settings);MP.nick="OldName";MP.loadSettings();
        if(!"LauncherName".equals(MP.nick))throw new AssertionError("Missing-file early return missed identity hook");
        System.out.println("KREYLI_NATIVE_FILTER: PASS transformed listing I/O and nickname settings paths");
    }
}
