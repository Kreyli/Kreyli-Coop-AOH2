package age.of.civilizations2.jakowski.lukasz.mp;
import com.badlogic.gdx.*;
import java.lang.reflect.Proxy;
public final class KreyliPointerTest {
 public static void main(String[] args){
  Input prior=Gdx.input;
  try{for(int held=-1;held<=2;held++){
   final int button=held;
   Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(object,method,values)->{
    if(method.getName().equals("isButtonPressed"))return values[0].equals(button);
    throw new AssertionError("Unexpected input call "+method.getName());
   });
   if(KreyliInputRecovery.pointerDown()!=(held>=0))throw new AssertionError("Pointer held check "+held);
  }}finally{Gdx.input=prior;}
  System.out.println("KREYLI_POINTER: PASS left, right and middle gestures preserved while held");
 }
}
