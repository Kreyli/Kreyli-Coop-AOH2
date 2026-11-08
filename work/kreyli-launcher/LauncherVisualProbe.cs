using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
class LauncherVisualProbe {
 static readonly BindingFlags Hidden=BindingFlags.Instance|BindingFlags.NonPublic;
 static object Field(Form form,string name){return form.GetType().GetField(name,Hidden).GetValue(form);}
 static void Set(Form form,string name,object value){form.GetType().GetField(name,Hidden).SetValue(form,value);}
 static void Refresh(Form form){form.GetType().GetMethod("RefreshState",Hidden).Invoke(form,null);}
 static void Pump(int ms){var end=DateTime.UtcNow.AddMilliseconds(ms);while(DateTime.UtcNow<end){Application.DoEvents();System.Threading.Thread.Sleep(10);}}
 static void Shot(Form form,string name){using(var bmp=new Bitmap(form.Width,form.Height)){form.DrawToBitmap(bmp,new Rectangle(0,0,bmp.Width,bmp.Height));bmp.Save(Path.Combine(Launcher.Root,name+".png"));}}
 [STAThread] static int Main(string[] args){try{
  Launcher.Root=Path.GetFullPath(args[0]);Directory.CreateDirectory(Launcher.Root);
  File.WriteAllText(Path.Combine(Launcher.Root,"launcher-profile.json"),"{\"nickname\":\"KreyliPlayer\"}");
  Application.EnableVisualStyles();using(var form=new LauncherWindow(new string[0])){
   form.Show();Pump(750);Shot(form,"01-install");
   File.WriteAllText(Path.Combine(Launcher.Root,"game.jar"),"visual fixture only");Refresh(form);
   ((Label)Field(form,"status")).Text="Все обновления установлены. Можно играть.";Shot(form,"02-play");
   var button=(Button)Field(form,"play");typeof(Control).GetMethod("OnMouseEnter",Hidden).Invoke(button,new object[]{EventArgs.Empty});Pump(500);Shot(form,"03-hover");
   if(((Timer)button.GetType().GetField("animation",Hidden).GetValue(button)).Enabled)throw new Exception("Hover timer did not settle");
   typeof(Control).GetMethod("OnMouseLeave",Hidden).Invoke(button,new object[]{EventArgs.Empty});Pump(300);
   Set(form,"pending",new UpdateManifest{version="1.0.4"});Refresh(form);Shot(form,"04-update");
   Set(form,"pending",null);Set(form,"transfer",true);GameInstall.Pause(false);Refresh(form);
   var bar=Field(form,"progress");bar.GetType().GetProperty("Value",Hidden|BindingFlags.Public).SetValue(bar,42,null);
   ((Label)Field(form,"status")).Text="Загрузка обновления · 42%";Pump(900);Shot(form,"05-download");
   if(((Timer)bar.GetType().GetField("animation",Hidden).GetValue(bar)).Enabled)throw new Exception("Progress timer did not settle");
   GameInstall.Pause(true);Refresh(form);((Label)Field(form,"status")).Text="Загрузка приостановлена";Shot(form,"06-paused");
   GameInstall.Pause(false);Set(form,"transfer",false);Refresh(form);
  }
  Console.WriteLine("PASS rendered install/play/hover/update/download/pause previews");return 0;
 }catch(Exception error){Console.Error.WriteLine(error);return 1;}}
}
