using System;using System.IO;using System.Threading;using System.Threading.Tasks;
class GameInstallTests{
 static int Main(string[] args){try{return Run(args);}catch(Exception e){Console.Error.WriteLine(e);return 1;}} static int Run(string[] args){Launcher.Root=Path.GetFullPath(args[0]);string work=Path.GetFullPath(args[1]);Directory.CreateDirectory(work);string config=Path.Combine(Launcher.Root,"game-location.json"),old=File.Exists(config)?File.ReadAllText(config):null;
  foreach(string bad in new[]{"../game.jar","saves/a","Kreyli Coop AOH2.exe","game/../../a","game/a:stream"}){bool rejected=false;try{GameInstall.Safe(work,bad);}catch{rejected=true;}if(!rejected)throw new Exception("Unsafe path accepted");}
  bool resumed=false;GameInstall.Pause(true);var gate=Task.Run(()=>{GameInstall.Gate.Wait();resumed=true;});Thread.Sleep(200);if(resumed)throw new Exception("Pause did not block");GameInstall.Pause(false);gate.Wait();
  try{string target=Path.Combine(work,"Installed game");int last=-1;Action<int,string> progress=(n,s)=>{if(n/10!=last){last=n/10;Console.WriteLine(s);}};
   GameInstall.Install(target,progress);Launcher.CheckGame();GameInstall.Verify(progress);
   Directory.CreateDirectory(Path.Combine(target,"saves"));File.WriteAllText(Path.Combine(target,"saves","keep.txt"),"save");File.WriteAllText(Path.Combine(target,"personal.txt"),"unrelated");
   GameInstall.Remove(progress);if(GameInstall.Installed||!File.Exists(Path.Combine(target,"saves","keep.txt"))||!File.Exists(Path.Combine(target,"personal.txt")))throw new Exception("Uninstall affected protected data");
   GameInstall.Install(target,progress);Launcher.CheckGame();if(!File.Exists(Path.Combine(target,"saves","keep.txt")))throw new Exception("Reinstall lost save");
   Console.WriteLine("PASS full remote-archive install, all asset hashes, bundled runtime preflight, remove game only, preserve save/unrelated files, reinstall, pause/resume, path guards");return 0;
  }finally{GameInstall.Pause(false);if(old==null){if(File.Exists(config))File.Delete(config);}else File.WriteAllText(config,old);}
 }
}
