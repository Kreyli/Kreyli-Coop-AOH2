using System;
using System.IO;
using System.Net;
using System.Text;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;
class LiveUpdateTests {
 [STAThread]
 static int Main(string[] args) {
  try {
  Launcher.Root=Path.GetFullPath(args[0]); Directory.CreateDirectory(Launcher.Root);
  File.WriteAllText(Path.Combine(Launcher.Root,".kreyli-install"),"Disposable live update test");
  File.WriteAllText(Path.Combine(Launcher.Root,"update-channel.json"),"{\"manifestUrl\":\"https://github.com/Kreyli/Kreyli-Coop-AOH2/releases/download/v1.0.9/update.json\"}");
  File.WriteAllText(Path.Combine(Launcher.Root,"installed-version.txt"),"1.0.8");
  File.WriteAllText(Path.Combine(Launcher.Root,"launcher-profile.json"),"{\"nickname\":\"Update QA\"}");
  Directory.CreateDirectory(Path.Combine(Launcher.Root,"saves"));
  File.WriteAllText(Path.Combine(Launcher.Root,"saves","keep.txt"),"preserve");
  var m=args.Length>1&&args[1]=="--poll"?WaitForOpenWindowUpdate():Launcher.CheckUpdate(); if(m==null||m.version!="1.0.9")throw new Exception("Expected release 1.0.9");
  string stage=Launcher.Stage(m,Console.WriteLine);
  Launcher.Apply(Launcher.Root,stage);
  foreach(var file in m.files)if(!Launcher.Hash(Launcher.SafePath(Launcher.Root,file.path)).Equals(file.sha256,StringComparison.OrdinalIgnoreCase))throw new Exception("Applied hash mismatch: "+file.path);
  if(File.ReadAllText(Path.Combine(Launcher.Root,"saves","keep.txt"))!="preserve"||!File.ReadAllText(Path.Combine(Launcher.Root,"launcher-profile.json")).Contains("Update QA"))throw new Exception("User data changed");
  if(Launcher.CheckUpdate()!=null)throw new Exception("Same version incorrectly offered");
  Console.WriteLine("PASS live GitHub manifest, all "+m.files.Length+" asset sizes and SHA-256, current-version check. Stage="+stage);return 0;
  } catch(Exception e) {Console.Error.WriteLine(e);return 1;}
 }
 static UpdateManifest WaitForOpenWindowUpdate(){
  if(Launcher.CheckUpdate()!=null)throw new Exception("The test must start before publication");
  const BindingFlags fields=BindingFlags.Instance|BindingFlags.NonPublic;
  using(var form=new LauncherWindow(new string[0])){
   form.ShowInTaskbar=false;
   form.WindowState=FormWindowState.Minimized;
   var pending=typeof(LauncherWindow).GetField("pending",fields);
   // Simulate a running campaign so the test can inspect the offer before applying it.
   typeof(LauncherWindow).GetField("gameRunning",fields).SetValue(form,true);
   form.Show();
   var until=DateTime.UtcNow.AddMinutes(4);
   var checking=typeof(LauncherWindow).GetField("checkingUpdates",fields);
   while((bool)checking.GetValue(form)&&DateTime.UtcNow<until){Application.DoEvents();Thread.Sleep(25);}
   if((bool)checking.GetValue(form)||pending.GetValue(form)!=null)throw new Exception("Initial background check did not finish on the old release");
   Console.WriteLine("OPEN_POLL_READY");
   while(DateTime.UtcNow<until){
    Application.DoEvents();
    var available=(UpdateManifest)pending.GetValue(form);
    if(available!=null){
     Console.WriteLine("PASS open launcher detected "+available.version+" during a game without applying it");
     if(File.ReadAllText(Path.Combine(Launcher.Root,"installed-version.txt"))!="1.0.8")throw new Exception("Update applied during gameplay");
     return available;
    }
    Thread.Sleep(25);
   }
   throw new Exception("Open launcher did not detect the update");
  }
 }
}







